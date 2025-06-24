package com.cookiebuild.cookiedough.service;

import java.util.List;
import java.util.UUID;

import com.cookiebuild.cookiedough.model.MinigameStats;
import com.cookiebuild.cookiedough.model.MinigameStatsId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;

public class MinigameStatsService {

    private final EntityManager entityManager;

    public MinigameStatsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public static final String MICROBATTLES = "microbattles";
    public static final String PITCHOUT = "pitchout";

    public MinigameStats getOrCreateStats(UUID playerId, String minigame) {
        MinigameStatsId id = new MinigameStatsId(playerId, minigame);
        MinigameStats stats = entityManager.find(MinigameStats.class, id);

        if (stats == null) {
            stats = new MinigameStats(playerId, minigame);
            saveStats(stats);
        }

        return stats;
    }

    public void saveStats(MinigameStats stats) {
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

    public boolean canAfford(UUID playerId, String minigame, int cost) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        return stats.getCoins() >= cost;
    }

    public boolean purchase(UUID playerId, String minigame, int cost) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        if (stats.removeCoins(cost)) {
            saveStats(stats);
            return true;
        }
        return false;
    }

    public void unlockKit(UUID playerId, String minigame, String kitName) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        stats.unlockKit(kitName);
        saveStats(stats);
    }

    public boolean hasUnlockedKit(UUID playerId, String minigame, String kitName) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        return stats.hasUnlockedKit(kitName);
    }

    public int getLevel(UUID playerId, String minigame) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        return stats.getLevel();
    }

    public int getCoins(UUID playerId, String minigame) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        return stats.getCoins();
    }

    public int getExperience(UUID playerId, String minigame) {
        MinigameStats stats = getOrCreateStats(playerId, minigame);
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
        MinigameStats stats = getOrCreateStats(playerId, minigame);
        stats.setLastSelectedKitName(kitName);
        stats.setLastSelectedKitLevel(level);
        saveStats(stats);
    }

    public void save(MinigameStats stats) {
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