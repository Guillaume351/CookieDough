package com.cookiebuild.cookiedough.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cookiebuild.cookiedough.model.GameStats;
import com.cookiebuild.cookiedough.model.PlayerData;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

public class PlayerStatsService {

    private final EntityManager entityManager;

    public PlayerStatsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Get all game stats for a player
     * 
     * @param playerId The UUID of the player
     * @return List of GameStats for the player
     */
    public List<GameStats> getPlayerStats(UUID playerId) {
        TypedQuery<GameStats> query = entityManager.createQuery(
                "SELECT gs FROM GameStats gs WHERE gs.player.id = :playerId",
                GameStats.class);
        query.setParameter("playerId", playerId);
        return query.getResultList();
    }

    /**
     * Get game stats for a player for a specific game type
     * 
     * @param playerId The UUID of the player
     * @param gameType The type of game
     * @return Optional containing the GameStats if found
     */
    public Optional<GameStats> getPlayerStatsByGameType(UUID playerId, String gameType) {
        try {
            TypedQuery<GameStats> query = entityManager.createQuery(
                    "SELECT gs FROM GameStats gs WHERE gs.player.id = :playerId AND gs.gameType = :gameType",
                    GameStats.class);
            query.setParameter("playerId", playerId);
            query.setParameter("gameType", gameType);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Get or create game stats for a player for a specific game type
     * 
     * @param player     The player
     * @param gameType   The type of game
     * @param statsClass The class of the stats to create if not found
     * @return The existing or newly created GameStats
     */
    public <T extends GameStats> T getOrCreatePlayerStats(PlayerData player, String gameType, Class<T> statsClass) {
        Optional<GameStats> existingStats = getPlayerStatsByGameType(player.getId(), gameType);

        if (existingStats.isPresent()) {
            return statsClass.cast(existingStats.get());
        }

        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            T newStats = statsClass.getDeclaredConstructor(PlayerData.class).newInstance(player);
            player.addGameStats(newStats);
            entityManager.persist(newStats);
            transaction.commit();
            return newStats;
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RuntimeException("Failed to create new stats for player", e);
        }
    }

    /**
     * Save game stats
     * 
     * @param stats The stats to save
     */
    public void saveStats(GameStats stats) {
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            if (stats.getId() == null) {
                entityManager.persist(stats);
            } else {
                entityManager.merge(stats);
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RuntimeException("Failed to save stats", e);
        }
    }

    /**
     * Update player stats after a game
     * 
     * @param player     The player
     * @param gameType   The type of game
     * @param won        Whether the player won the game
     * @param kills      Number of kills
     * @param deaths     Number of deaths
     * @param statsClass The class of the stats to update
     * @return The updated stats
     */
    public <T extends GameStats> T updatePlayerStatsAfterGame(
            PlayerData player,
            String gameType,
            boolean won,
            int kills,
            int deaths,
            Class<T> statsClass) {

        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            T stats = getOrCreatePlayerStats(player, gameType, statsClass);

            stats.incrementGamesPlayed();
            if (won) {
                stats.incrementGamesWon();
            } else {
                stats.incrementGamesLost();
            }

            stats.addKills(kills);
            stats.addDeaths(deaths);

            if (stats.getId() == null) {
                entityManager.persist(stats);
            } else {
                entityManager.merge(stats);
            }

            transaction.commit();
            return stats;
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RuntimeException("Failed to update player stats after game", e);
        }
    }
}