package com.cookiebuild.cookiedough.service;

import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Date;

import com.cookiebuild.cookiedough.model.CoinTransaction;
import com.cookiebuild.cookiedough.model.MinigameProgression;
import com.cookiebuild.cookiedough.model.MinigameProgressionId;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/** Transaction-scoped progression operations safe to call from different tasks. */
public class MinigameProgressionService {
    private record RewardApplication(MinigameProgression progression, boolean applied) {
    }
    public static final String MICROBATTLES = "microbattles";
    public static final String PITCHOUT = "pitchout";
    public static final String SKYWARS = "skywars";
    public static final String BUILDBATTLES = "buildbattles";
    public static final String TURFWARS = "turfwars";

    private final EntityManager legacyEntityManager;

    public MinigameProgressionService(EntityManager entityManager) {
        this.legacyEntityManager = entityManager;
    }

    public MinigameProgression getOrCreateStats(UUID playerId, String minigame) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            try {
                transaction.begin();
                MinigameProgression stats = findOrCreate(em, playerId, minigame);
                transaction.commit();
                return stats;
            } catch (RuntimeException error) {
                rollback(transaction);
                throw error;
            }
        }
    }

    public void saveStats(MinigameProgression stats) {
        inTransaction(em -> {
            em.merge(stats);
            return null;
        });
    }

    public boolean canAfford(UUID playerId, String minigame, int cost) {
        if (cost < 0) {
            return false;
        }
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            PlayerData playerData = em.find(PlayerData.class, playerId);
            return playerData != null && playerData.getCoins() >= cost;
        }
    }

    public boolean purchase(UUID playerId, String minigame, int cost) {
        if (cost < 0) {
            return false;
        }
        boolean purchased = inTransaction(em -> {
            PlayerData playerData = em.find(PlayerData.class, playerId,
                    jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (playerData == null || !playerData.removeCoins(cost)) {
                return false;
            }
            appendCoinTransaction(em, playerData, -cost,
                    "purchase:" + minigame + ":" + UUID.randomUUID());
            return true;
        });
        if (purchased) {
            FunnelTelemetry.record(playerId, FunnelTelemetry.Event.KIT_PURCHASED,
                    "game=" + minigame + " purchase=legacy cost=" + cost);
        }
        return purchased;
    }

    /** Deducts coins and records the unlock in one database transaction. */
    public boolean purchaseAndUnlockKit(UUID playerId, String minigame, String kitKey, int cost) {
        if (cost < 0 || kitKey == null || kitKey.isBlank()) {
            return false;
        }
        boolean purchased = inTransaction(em -> {
            PlayerData playerData = em.find(PlayerData.class, playerId,
                    jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (playerData == null) {
                return false;
            }
            MinigameProgression stats = findOrCreate(em, playerId, minigame);
            if (stats.hasUnlockedKit(kitKey)) {
                return true;
            }
            if (!playerData.removeCoins(cost)) {
                return false;
            }
            stats.unlockKit(kitKey);
            appendCoinTransaction(em, playerData, -cost, "kit:" + minigame + ":" + kitKey);
            return true;
        });
        if (purchased) {
            FunnelTelemetry.record(playerId, FunnelTelemetry.Event.KIT_PURCHASED,
                    "game=" + minigame + " kit=" + kitKey + " cost=" + cost);
        }
        return purchased;
    }

    /** Applies XP and global coins atomically; throws when the reward cannot be saved. */
    public MinigameProgression applyReward(UUID playerId, String minigame, int experience, int coins) {
        return applyReward(playerId, minigame, experience, coins,
                "match-reward:" + minigame + ":" + UUID.randomUUID());
    }

    public MinigameProgression applyReward(UUID playerId, String minigame, int experience, int coins,
            String source) {
        if (experience < 0 || coins < 0) {
            throw new IllegalArgumentException("Rewards cannot be negative");
        }
        RewardApplication result = inTransaction(em -> {
            PlayerData playerData = em.find(PlayerData.class, playerId,
                    jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (playerData == null) {
                throw new IllegalArgumentException("Player not found: " + playerId);
            }
            MinigameProgression stats = findOrCreate(em, playerId, minigame);
            if (coinTransactionExists(em, playerId, source)) {
                return new RewardApplication(stats, false);
            }
            stats.addExperience(experience);
            playerData.addCoins(coins);
            appendCoinTransaction(em, playerData, coins, source);
            return new RewardApplication(stats, true);
        });
        if (result.applied()) {
            FunnelTelemetry.record(playerId, FunnelTelemetry.Event.REWARD_CLAIMED,
                    "source=" + source + " game=" + minigame + " xp=" + experience + " coins=" + coins);
        }
        return result.progression();
    }

    /** Applies a goal reward once using the same coin transaction ledger as match rewards. */
    public boolean claimGoalReward(UUID playerId, String minigame, int experience, int coins, String rewardKey) {
        if (rewardKey == null || rewardKey.isBlank() || experience < 0 || coins < 0) return false;
        String normalizedGame = supportedMinigameKey(minigame);
        if (normalizedGame == null) return false;
        RewardApplication result = inTransaction(em -> {
            PlayerData playerData = em.find(PlayerData.class, playerId,
                    jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (playerData == null) return new RewardApplication(null, false);
            MinigameProgression stats = findOrCreate(em, playerId, normalizedGame);
            String source = "goal:" + rewardKey;
            if (coinTransactionExists(em, playerId, source)) return new RewardApplication(stats, false);
            stats.addExperience(experience);
            playerData.addCoins(coins);
            appendCoinTransaction(em, playerData, coins, source);
            return new RewardApplication(stats, true);
        });
        return result.applied();
    }

    static String supportedMinigameKey(String minigame) {
        return switch (minigame == null ? "" : minigame.toLowerCase(java.util.Locale.ROOT)) {
            case "microbattles" -> MICROBATTLES;
            case "pitchout" -> PITCHOUT;
            case "skywars" -> SKYWARS;
            case "buildbattles" -> BUILDBATTLES;
            case "turfwars" -> TURFWARS;
            default -> null;
        };
    }

    /** Claims globally idempotent rewards using the coin transaction ledger. */
    public boolean claimGlobalReward(UUID playerId, String rewardKey, int coins) {
        if (rewardKey == null || rewardKey.isBlank() || coins < 0) {
            return false;
        }
        return claimGlobalRewards(playerId, Map.of(rewardKey, coins)).contains(rewardKey);
    }

    public Set<String> claimGlobalRewards(UUID playerId, Map<String, Integer> rewards) {
        if (rewards == null || rewards.isEmpty() || rewards.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() < 0)) {
            return Set.of();
        }
        return inTransaction(em -> {
            PlayerData player = em.find(PlayerData.class, playerId,
                    jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (player == null) {
                return Set.<String>of();
            }
            Set<String> claimed = new LinkedHashSet<>();
            rewards.forEach((key, coins) -> {
                String source = "goal:" + key;
                if (!coinTransactionExists(em, playerId, source)) {
                    player.addCoins(coins);
                    appendCoinTransaction(em, player, coins, source);
                    claimed.add(key);
                }
            });
            return Set.copyOf(claimed);
        });
    }

    public void unlockKit(UUID playerId, String minigame, String kitName) {
        inTransaction(em -> {
            findOrCreate(em, playerId, minigame).unlockKit(kitName);
            return null;
        });
    }

    public boolean hasUnlockedKit(UUID playerId, String minigame, String kitName) {
        return getOrCreateStats(playerId, minigame).hasUnlockedKit(kitName);
    }

    public int getLevel(UUID playerId, String minigame) {
        return getOrCreateStats(playerId, minigame).getLevel();
    }

    public int getCoins(UUID playerId, String minigame) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            PlayerData playerData = em.find(PlayerData.class, playerId);
            return playerData == null ? 0 : playerData.getCoins();
        }
    }

    public int getExperience(UUID playerId, String minigame) {
        return getOrCreateStats(playerId, minigame).getExperience();
    }

    public String getPlayerName(UUID playerId) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            PlayerData player = em.find(PlayerData.class, playerId);
            return player == null || player.getName() == null
                    ? playerId.toString().substring(0, 8) : player.getName();
        }
    }

    /** @deprecated service methods are transaction-scoped; do not use this manager for new work. */
    @Deprecated
    public EntityManager getEntityManager() {
        return legacyEntityManager;
    }

    public void setLastSelectedKit(UUID playerId, String minigame, String kitName, int level) {
        inTransaction(em -> {
            MinigameProgression stats = findOrCreate(em, playerId, minigame);
            stats.setLastSelectedKitName(kitName);
            stats.setLastSelectedKitLevel(level);
            return null;
        });
        FunnelTelemetry.record(playerId, FunnelTelemetry.Event.KIT_SELECTED,
                "game=" + minigame + " kit=" + kitName + " level=" + level);
    }

    public void save(MinigameProgression stats) {
        saveStats(stats);
    }

    private MinigameProgression findOrCreate(EntityManager em, UUID playerId, String minigame) {
        MinigameProgressionId id = new MinigameProgressionId(playerId, minigame);
        MinigameProgression stats = em.find(MinigameProgression.class, id);
        if (stats == null) {
            stats = new MinigameProgression(playerId, minigame);
            em.persist(stats);
        }
        return stats;
    }

    private boolean coinTransactionExists(EntityManager em, UUID playerId, String source) {
        return em.createQuery("SELECT COUNT(t) FROM CoinTransaction t WHERE t.player.id = :playerId "
                        + "AND t.source = :source", Long.class)
                .setParameter("playerId", playerId).setParameter("source", source)
                .getSingleResult() > 0;
    }

    private void appendCoinTransaction(EntityManager em, PlayerData player, int amount, String source) {
        if (source == null || source.isBlank() || source.length() > 180) {
            throw new IllegalArgumentException("Coin transaction source must contain 1-180 characters");
        }
        em.persist(new CoinTransaction(player, amount, source, new Date()));
    }

    private <T> T inTransaction(TransactionWork<T> work) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            try {
                transaction.begin();
                T result = work.apply(em);
                transaction.commit();
                return result;
            } catch (RuntimeException error) {
                rollback(transaction);
                throw error;
            }
        }
    }

    private void rollback(EntityTransaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T apply(EntityManager entityManager);
    }
}
