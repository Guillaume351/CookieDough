package com.cookiebuild.cookiedough.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;

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
     * Get all match performances for a player
     *
     * @param playerId The UUID of the player
     * @return List of PlayerMatchPerformance for the player
     */
    public List<PlayerMatchPerformance> getPlayerPerformances(UUID playerId) {
        TypedQuery<PlayerMatchPerformance> query = entityManager.createQuery(
                "SELECT p FROM PlayerMatchPerformance p " +
                        "JOIN FETCH p.match m " +
                        "LEFT JOIN FETCH m.winners " +
                        "WHERE p.player.id = :playerId",
                PlayerMatchPerformance.class);
        query.setParameter("playerId", playerId);
        return query.getResultList();
    }

    /**
     * Get the top player for a specific game mode this week
     *
     * @param gameMode The game mode to check
     * @return PlayerData of the top player, or null if none found
     */
    public PlayerData getTopPlayerThisWeek(String gameMode) {
        // Calculate start of week (Monday)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfWeek = cal.getTime();

        // Query to get the player with the most wins in the specified game mode this
        // week
        TypedQuery<PlayerData> query = entityManager.createQuery(
                "SELECT p.player FROM PlayerMatchPerformance p " +
                        "JOIN p.match m " +
                        "WHERE m.gameType = :gameType AND m.endTime >= :startOfWeek " +
                        "GROUP BY p.player " +
                        "ORDER BY SUM(CASE WHEN p.player IN (SELECT w FROM m.winners w) THEN 1 ELSE 0 END) DESC",
                PlayerData.class);
        query.setParameter("gameType", gameMode);
        query.setParameter("startOfWeek", startOfWeek);
        query.setMaxResults(1);

        try {
            return query.getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            return null;
        }
    }

    /**
     * Get match performances for a player in a specific match
     *
     * @param playerId The UUID of the player
     * @param matchId  The UUID of the match
     * @return Optional containing the PlayerMatchPerformance if found
     */
    public Optional<PlayerMatchPerformance> getPlayerPerformanceInMatch(UUID playerId, UUID matchId) {
        try {
            TypedQuery<PlayerMatchPerformance> query = entityManager.createQuery(
                    "SELECT p FROM PlayerMatchPerformance p WHERE p.player.id = :playerId AND p.match.id = :matchId",
                    PlayerMatchPerformance.class);
            query.setParameter("playerId", playerId);
            query.setParameter("matchId", matchId);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Get or create match performance for a player in a specific match
     *
     * @param player The player
     * @param match  The match
     * @return The existing or newly created PlayerMatchPerformance
     */
    public PlayerMatchPerformance getOrCreateMatchPerformance(PlayerData player, Match match) {
        Optional<PlayerMatchPerformance> existingPerformance = getPlayerPerformanceInMatch(player.getId(),
                match.getId());

        if (existingPerformance.isPresent()) {
            return existingPerformance.get();
        }

        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            PlayerMatchPerformance newPerformance = new PlayerMatchPerformance(match, player);
            entityManager.persist(newPerformance);
            transaction.commit();
            return newPerformance;
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RuntimeException("Failed to create new match performance for player", e);
        }
    }

    /**
     * Save match performance
     *
     * @param performance The performance to save
     */
    public void savePerformance(PlayerMatchPerformance performance) {
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            if (performance.getId() == null) {
                entityManager.persist(performance);
            } else {
                entityManager.merge(performance);
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RuntimeException("Failed to save match performance", e);
        }
    }

    /**
     * Update player performance after a match
     *
     * @param player              The player
     * @param match               The match
     * @param kills               Number of kills
     * @param deaths              Number of deaths
     * @param assists             Number of assists
     * @param gameSpecificMetrics Game-specific metrics as JSON string
     * @return The updated performance
     */
    public PlayerMatchPerformance updatePlayerPerformanceAfterMatch(
            PlayerData player,
            Match match,
            int kills,
            int deaths,
            int assists,
            String gameSpecificMetrics) {

        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            PlayerMatchPerformance performance = getOrCreateMatchPerformance(player, match);

            performance.setKillsInMatch(kills);
            performance.setDeathsInMatch(deaths);
            performance.setAssistsInMatch(assists);
            performance.setGameSpecificMetrics(gameSpecificMetrics);

            if (performance.getId() == null) {
                entityManager.persist(performance);
            } else {
                entityManager.merge(performance);
            }

            transaction.commit();
            return performance;
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RuntimeException("Failed to update player performance after match", e);
        }
    }

    /**
     * Get player data by UUID
     *
     * @param playerId The UUID of the player
     * @return The PlayerData object if found, null otherwise
     */
    public PlayerData getPlayerData(UUID playerId) {
        try {
            return entityManager.find(PlayerData.class, playerId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the number of wins for a player in a specific game mode this week
     *
     * @param playerId The UUID of the player
     * @param gameMode The game mode to check
     * @return Number of wins
     */
    public int getWinsThisWeek(UUID playerId, String gameMode) {
        // Calculate start of week (Monday)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfWeek = cal.getTime();

        // Query to count wins for the player in the specified game mode this week
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(m) FROM Match m " +
                        "JOIN m.winners w " +
                        "WHERE m.gameType = :gameType AND m.endTime >= :startOfWeek AND w.id = :playerId",
                Long.class);
        query.setParameter("gameType", gameMode);
        query.setParameter("startOfWeek", startOfWeek);
        query.setParameter("playerId", playerId);

        try {
            return query.getSingleResult().intValue();
        } catch (jakarta.persistence.NoResultException e) {
            return 0;
        }
    }

    /**
     * Get the top players for a specific game mode this month
     *
     * @param gameMode The game mode to check
     * @param limit    The maximum number of players to return
     * @return List of PlayerData of the top players
     */
    public List<PlayerData> getTopPlayersThisMonth(String gameMode, int limit) {
        // Calculate start of month
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfMonth = cal.getTime();

        // Query to get players with the most wins in the specified game mode this month
        TypedQuery<PlayerData> query = entityManager.createQuery(
                "SELECT p.player FROM PlayerMatchPerformance p " +
                        "JOIN p.match m " +
                        "WHERE m.gameType = :gameType AND m.endTime >= :startOfMonth " +
                        "GROUP BY p.player " +
                        "ORDER BY SUM(CASE WHEN p.player IN (SELECT w FROM m.winners w) THEN 1 ELSE 0 END) DESC",
                PlayerData.class);
        query.setParameter("gameType", gameMode);
        query.setParameter("startOfMonth", startOfMonth);
        query.setMaxResults(limit);

        try {
            return query.getResultList();
        } catch (jakarta.persistence.NoResultException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Get the number of wins for a player in a specific game mode this month
     *
     * @param playerId The UUID of the player
     * @param gameMode The game mode to check
     * @return Number of wins
     */
    public int getWinsThisMonth(UUID playerId, String gameMode) {
        // Calculate start of month
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfMonth = cal.getTime();

        // Query to count wins for the player in the specified game mode this month
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(DISTINCT m) FROM Match m " +
                        "JOIN m.winners w " +
                        "WHERE m.gameType = :gameMode " +
                        "AND m.endTime >= :startOfMonth " +
                        "AND w.id = :playerId",
                Long.class);
        query.setParameter("gameMode", gameMode);
        query.setParameter("startOfMonth", startOfMonth);
        query.setParameter("playerId", playerId);

        try {
            return query.getSingleResult().intValue();
        } catch (jakarta.persistence.NoResultException e) {
            return 0;
        }
    }
}
