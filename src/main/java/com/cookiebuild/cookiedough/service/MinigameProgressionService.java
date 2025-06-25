package com.cookiebuild.cookiedough.service;

import java.util.List;
import java.util.UUID;

import com.cookiebuild.cookiedough.model.MinigameProgression;
import com.cookiebuild.cookiedough.model.MinigameProgressionId;
import com.cookiebuild.cookiedough.model.PlayerData;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;

public class MinigameProgressionService {

    private final EntityManager entityManager;

    public MinigameProgressionService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public static final String MICROBATTLES = "microbattles";
    public static final String PITCHOUT = "pitchout";

    public MinigameProgression getOrCreateStats(UUID playerId, String minigame) {
        MinigameProgressionId id = new MinigameProgressionId(playerId, minigame);
        MinigameProgression stats = entityManager.find(MinigameProgression.class, id);

        if (stats == null) {
            stats = new MinigameProgression(playerId, minigame);
            saveStats(stats);
        }

        return stats;
    }

    public void saveStats(MinigameProgression stats) {
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            if (!transaction.isActive()) {
                transaction.begin();
            }
            entityManager.merge(stats);
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            e.printStackTrace();
        }
    }

    /**
     * Check if player can afford a purchase (uses PlayerData coins)
     */
    public boolean canAfford(UUID playerId, String minigame, int cost) {
        PlayerData playerData = entityManager.find(PlayerData.class, playerId);
        return playerData != null && playerData.getCoins() >= cost;
    }

    /**
     * Purchase an item using PlayerData coins
     */
    public boolean purchase(UUID playerId, String minigame, int cost) {
        PlayerData playerData = entityManager.find(PlayerData.class, playerId);
        if (playerData != null && playerData.removeCoins(cost)) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                if (!transaction.isActive()) {
                    transaction.begin();
                }
                entityManager.merge(playerData);
                transaction.commit();
                return true;
            } catch (Exception e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                e.printStackTrace();
            }
        }
        return false;
    }

    public void unlockKit(UUID playerId, String minigame, String kitName) {
        MinigameProgression stats = getOrCreateStats(playerId, minigame);
        stats.unlockKit(kitName);
        saveStats(stats);
    }

    public boolean hasUnlockedKit(UUID playerId, String minigame, String kitName) {
        MinigameProgression stats = getOrCreateStats(playerId, minigame);
        return stats.hasUnlockedKit(kitName);
    }

    public int getLevel(UUID playerId, String minigame) {
        MinigameProgression stats = getOrCreateStats(playerId, minigame);
        return stats.getLevel();
    }

    /**
     * Get player coins from PlayerData (global coins, not minigame-specific)
     */
    public int getCoins(UUID playerId, String minigame) {
        PlayerData playerData = entityManager.find(PlayerData.class, playerId);
        return playerData != null ? playerData.getCoins() : 0;
    }

    public int getExperience(UUID playerId, String minigame) {
        MinigameProgression stats = getOrCreateStats(playerId, minigame);
        return stats.getExperience();
    }

    public String getPlayerName(UUID playerId) {
        try {
            TypedQuery<String> query = entityManager.createQuery(
                    "SELECT pd.playerName FROM PlayerData pd WHERE pd.playerId = :playerId",
                    String.class);
            query.setParameter("playerId", playerId);
            query.setMaxResults(1);

            List<String> results = query.getResultList();
            return results.isEmpty() ? playerId.toString().substring(0, 8) : results.get(0);
        } catch (Exception e) {
            e.printStackTrace();
            return playerId.toString().substring(0, 8);
        }
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public void setLastSelectedKit(UUID playerId, String minigame, String kitName, int level) {
        MinigameProgression stats = getOrCreateStats(playerId, minigame);
        stats.setLastSelectedKitName(kitName);
        stats.setLastSelectedKitLevel(level);
        saveStats(stats);
    }

    public void save(MinigameProgression stats) {
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            if (!transaction.isActive()) {
                transaction.begin();
            }
            entityManager.merge(stats);
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            e.printStackTrace();
        }
    }
}