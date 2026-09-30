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
import jakarta.persistence.LockModeType;

/** Transaction-scoped progression operations safe to call from different tasks. */
public class MinigameProgressionService {
    private record RewardApplication(MinigameProgression progression, boolean applied, int bonus) {
        RewardApplication(MinigameProgression progression, boolean applied) {
            this(progression, applied, 0);
        }
    }
    public static final String MICROBATTLES = "microbattles";
    public static final String PITCHOUT = "pitchout";
    public static final String SKYWARS = "skywars";
    public static final String BUILDBATTLES = "buildbattles";
    public static final String TURFWARS = "turfwars";
    public static final String BEDWARS = "bedwars";
    public static final String FATKING = "fatking";
    public static final String NOMADWARS = "nomadwars";

    /** Live coin multiplier for match rewards (for example x2 during a Soirée Cookie). */
    @FunctionalInterface
    public interface MatchCoinBonus {
        int multiplier();
    }

    /** Called after commit when a match reward received an event bonus. */
    @FunctionalInterface
    public interface MatchCoinBonusListener {
        void onBonus(UUID playerId, int bonusCoins);
    }

    private static volatile MatchCoinBonus matchCoinBonus = () -> 1;
    private static volatile MatchCoinBonusListener matchCoinBonusListener = (playerId, bonus) -> { };

    private final EntityManager legacyEntityManager;

    public static void setMatchCoinBonus(MatchCoinBonus bonus, MatchCoinBonusListener listener) {
        matchCoinBonus = bonus == null ? () -> 1 : bonus;
        matchCoinBonusListener = listener == null ? (playerId, extra) -> { } : listener;
    }

    /** Extra coins granted on top of a base match reward; never negative. */
    public static int eventBonusCoins(int baseCoins, int multiplier) {
        if (baseCoins <= 0 || multiplier <= 1) return 0;
        long bonus = (long) baseCoins * (Math.min(multiplier, 5) - 1);
        return (int) Math.min(bonus, 10_000L);
    }

    static String eventBonusSource(String source) {
        String bonusSource = "event-bonus:" + source;
        return bonusSource.length() <= 180 ? bonusSource : bonusSource.substring(0, 180);
    }

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

    /**
     * Detached full-row saves cannot participate in the player-first locking
     * contract and can overwrite a concurrent mobile purchase. Use the atomic
     * service operations instead.
     */
    @Deprecated(forRemoval = true)
    public void saveStats(MinigameProgression stats) {
        throw new UnsupportedOperationException("Use an atomic MinigameProgressionService operation");
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
                return new RewardApplication(stats, false, 0);
            }
            stats.addExperience(experience);
            playerData.addCoins(coins);
            appendCoinTransaction(em, playerData, coins, source);
            int bonus = eventBonusCoins(coins, safeMultiplier());
            if (bonus > 0) {
                playerData.addCoins(bonus);
                appendCoinTransaction(em, playerData, bonus, eventBonusSource(source));
            }
            return new RewardApplication(stats, true, bonus);
        });
        if (result.applied()) {
            FunnelTelemetry.record(playerId, FunnelTelemetry.Event.REWARD_CLAIMED,
                    "source=" + source + " game=" + minigame + " xp=" + experience + " coins=" + coins
                            + " event_bonus=" + result.bonus());
            if (result.bonus() > 0) {
                try {
                    matchCoinBonusListener.onBonus(playerId, result.bonus());
                } catch (RuntimeException ignored) {
                    // The bonus is committed; the notice is best effort.
                }
            }
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

    public static String supportedMinigameKey(String minigame) {
        return switch (minigame == null ? "" : minigame.toLowerCase(java.util.Locale.ROOT)) {
            case "microbattles" -> MICROBATTLES;
            case "pitchout" -> PITCHOUT;
            case "skywars" -> SKYWARS;
            case "buildbattles" -> BUILDBATTLES;
            case "turfwars" -> TURFWARS;
            case "bedwars" -> BEDWARS;
            case "fatking" -> FATKING;
            case "nomadwars" -> NOMADWARS;
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

    @Deprecated(forRemoval = true)
    public void save(MinigameProgression stats) {
        saveStats(stats);
    }

    private static int safeMultiplier() {
        try {
            return Math.max(1, matchCoinBonus.multiplier());
        } catch (RuntimeException error) {
            return 1;
        }
    }

    MinigameProgression findOrCreate(EntityManager em, UUID playerId, String minigame) {
        // Every progression read-modify-write transaction serializes on the
        // durable player row first. The mobile shop follows the same order, so
        // selection, unlock, XP and coin writes cannot overwrite one another.
        PlayerData player = em.find(PlayerData.class, playerId, LockModeType.PESSIMISTIC_WRITE);
        if (player == null) {
            throw new IllegalStateException("Cannot initialize progression before player data: " + playerId);
        }
        MinigameProgressionId id = new MinigameProgressionId(playerId, minigame);
        MinigameProgression stats = em.find(MinigameProgression.class, id, LockModeType.PESSIMISTIC_WRITE);
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
