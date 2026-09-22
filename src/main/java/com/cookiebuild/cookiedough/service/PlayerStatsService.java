package com.cookiebuild.cookiedough.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.MinigameProgression;
import com.cookiebuild.cookiedough.model.MinigameProgressionId;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;
import com.cookiebuild.cookiedough.model.PlayerSession;
import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

public class PlayerStatsService {
    public record ProgressionSnapshot(int level, int experience, int nextLevelExperience) {
        public static ProgressionSnapshot empty() {
            return new ProgressionSnapshot(1, 0, 100);
        }
    }

    private final EntityManager entityManager;

    // Constructor for backward compatibility - prefer static methods for better
    // resource
    // management
    public PlayerStatsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // Static helper methods with proper EntityManager lifecycle management

    /**
     * Get player performances with proper resource management
     */
    public static List<PlayerMatchPerformance> getPlayerPerformancesStatic(UUID playerId) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            TypedQuery<PlayerMatchPerformance> query = em.createQuery(
                    "SELECT DISTINCT p FROM PlayerMatchPerformance p " +
                            "JOIN FETCH p.match m " +
                            "LEFT JOIN FETCH m.winners " +
                            "WHERE p.player.id = :playerId",
                    PlayerMatchPerformance.class);
            query.setParameter("playerId", playerId);
            return query.getResultList();
        }
    }

    /**
     * Get player data with proper resource management
     */
    public static PlayerData getPlayerDataStatic(UUID playerId) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            return em.find(PlayerData.class, playerId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get top players for a specific game mode this month with proper resource
     * management.
     * Fixed HQL syntax for better HikariCP compatibility.
     */
    public static List<PlayerData> getTopPlayersThisMonthStatic(String gameMode, int limit) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            // Calculate start of month
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date startOfMonth = cal.getTime();

            // Use more standard HQL query - count wins by checking if player is in winners
            // collection
            TypedQuery<PlayerData> query = em.createQuery(
                    "SELECT p.player FROM PlayerMatchPerformance p " +
                            "JOIN p.match m " +
                            "WHERE m.gameType = :gameType AND m.endTime >= :startOfMonth " +
                            "GROUP BY p.player " +
                            "ORDER BY SUM(CASE WHEN p.player IN (SELECT w FROM m.winners w) THEN 1 ELSE 0 END) DESC",
                    PlayerData.class);
            query.setParameter("gameType", gameMode);
            query.setParameter("startOfMonth", startOfMonth);
            query.setMaxResults(limit);
            return query.getResultList();
        } catch (Exception e) {
            // Add error handling to help debug leaderboard issues
            CookieDough.getInstance().getLogger()
                    .severe("Failed to get top players for " + gameMode + ": " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Get top player this week with proper resource management
     * Fixed HQL syntax for better HikariCP compatibility.
     */
    public static PlayerData getTopPlayerThisWeekStatic(String gameMode) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            // Calculate start of week (Monday)
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date startOfWeek = cal.getTime();

            // Query to get the player with the most wins in the specified game mode this
            // week - standardized syntax
            TypedQuery<PlayerData> query = em.createQuery(
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
        } catch (Exception e) {
            // Add error handling to help debug statue issues
            CookieDough.getInstance().getLogger()
                    .warning("Failed to get top player for " + gameMode + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get wins this week with proper resource management
     */
    public static int getWinsThisWeekStatic(UUID playerId, String gameMode) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            // Calculate start of week (Monday)
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date startOfWeek = cal.getTime();

            // Query to count wins for the player in the specified game mode this week
            TypedQuery<Long> query = em.createQuery(
                    "SELECT COUNT(m) FROM PlayerMatchPerformance p " +
                            "JOIN p.match m " +
                            "WHERE p.player.id = :playerId AND m.gameType = :gameType " +
                            "AND m.endTime >= :startOfWeek AND p.player IN (SELECT w FROM m.winners w)",
                    Long.class);
            query.setParameter("playerId", playerId);
            query.setParameter("gameType", gameMode);
            query.setParameter("startOfWeek", startOfWeek);

            return query.getSingleResult().intValue();
        } catch (Exception e) {
            CookieDough.getInstance().getLogger().warning(
                    "Failed to get wins this week for player " + playerId + " in " + gameMode + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get wins this month with proper resource management
     */
    public static int getWinsThisMonthStatic(UUID playerId, String gameMode) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            // Calculate start of month
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date startOfMonth = cal.getTime();

            // Query to count wins for the player in the specified game mode this month
            TypedQuery<Long> query = em.createQuery(
                    "SELECT COUNT(m) FROM PlayerMatchPerformance p " +
                            "JOIN p.match m " +
                            "WHERE p.player.id = :playerId AND m.gameType = :gameType " +
                            "AND m.endTime >= :startOfMonth AND p.player IN (SELECT w FROM m.winners w)",
                    Long.class);
            query.setParameter("playerId", playerId);
            query.setParameter("gameType", gameMode);
            query.setParameter("startOfMonth", startOfMonth);

            return query.getSingleResult().intValue();
        } catch (Exception e) {
            CookieDough.getInstance().getLogger().warning(
                    "Failed to get wins this month for player " + playerId + " in " + gameMode + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get total play time with proper resource management
     */
    public static Long getTotalPlayTimeStatic(UUID playerId) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            // Query to sum the duration of all completed sessions for the player
            TypedQuery<Long> query = em.createQuery(
                    "SELECT COALESCE(SUM(ps.duration), 0) FROM PlayerSession ps " +
                            "WHERE ps.playerData.id = :playerId AND ps.duration IS NOT NULL",
                    Long.class);
            query.setParameter("playerId", playerId);
            return query.getSingleResult();
        } catch (Exception e) {
            return 0L;
        }
    }

    public static List<PlayerSession> getPlayerSessionsStatic(UUID playerId) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            return em.createQuery("SELECT s FROM PlayerSession s WHERE s.playerData.id = :playerId "
                            + "ORDER BY s.startTime DESC", PlayerSession.class)
                    .setParameter("playerId", playerId).getResultList();
        }
    }

    public static int getCoinsStatic(UUID playerId) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            Integer coins = em.createQuery("SELECT p.coins FROM PlayerData p WHERE p.id = :playerId", Integer.class)
                    .setParameter("playerId", playerId).getResultStream().findFirst().orElse(0);
            return coins == null ? 0 : coins;
        }
    }

    public static ProgressionSnapshot getProgressionStatic(UUID playerId, String minigame) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            MinigameProgression progression = em.find(MinigameProgression.class,
                    new MinigameProgressionId(playerId, minigame));
            return progression == null ? ProgressionSnapshot.empty()
                    : new ProgressionSnapshot(progression.getLevel(), progression.getExperience(),
                            progression.getExperienceForNextLevel());
        }
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Get player data by UUID (legacy method)
     * 
     * @deprecated Use {@link #getPlayerDataStatic(UUID)} instead for better
     *             resource management
     */
    @Deprecated
    public PlayerData getPlayerData(UUID playerId) {
        if (entityManager == null) {
            return getPlayerDataStatic(playerId);
        }
        try {
            return entityManager.find(PlayerData.class, playerId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get all match performances for a player. Used for legacy compatibility.
     * 
     * @deprecated Use {@link #getPlayerPerformancesStatic(UUID)} instead for better
     *             resource management.
     *             This method will keep a connection open until the service is
     *             disposed.
     */
    @Deprecated
    public List<PlayerMatchPerformance> getPlayerPerformances(UUID playerId) {
        if (entityManager == null) {
            return getPlayerPerformancesStatic(playerId);
        }
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

    /**
     * Get recent match performances for a player, sorted by match end time
     * descending.
     *
     * @param playerId The UUID of the player
     * @param limit    The maximum number of performances to return
     * @return List of recent PlayerMatchPerformance for the player
     */
    public List<PlayerMatchPerformance> getRecentPlayerPerformances(UUID playerId, int limit) {
        TypedQuery<PlayerMatchPerformance> query = entityManager.createQuery(
                "SELECT p FROM PlayerMatchPerformance p " +
                        "JOIN FETCH p.match m " +
                        "LEFT JOIN FETCH m.winners " +
                        "WHERE p.player.id = :playerId " +
                        "ORDER BY m.endTime DESC",
                PlayerMatchPerformance.class);
        query.setParameter("playerId", playerId);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    /**
     * Get total kills for a player across all matches.
     *
     * @param playerId The UUID of the player
     * @return Total number of kills
     */
    public long getTotalKills(UUID playerId) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT SUM(p.killsInMatch) FROM PlayerMatchPerformance p WHERE p.player.id = :playerId",
                Long.class);
        query.setParameter("playerId", playerId);
        Long result = query.getSingleResult();
        return result != null ? result : 0;
    }

    /**
     * Get total deaths for a player across all matches.
     *
     * @param playerId The UUID of the player
     * @return Total number of deaths
     */
    public long getTotalDeaths(UUID playerId) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT SUM(p.deathsInMatch) FROM PlayerMatchPerformance p WHERE p.player.id = :playerId",
                Long.class);
        query.setParameter("playerId", playerId);
        Long result = query.getSingleResult();
        return result != null ? result : 0;
    }

    /**
     * Get total assists for a player across all matches.
     *
     * @param playerId The UUID of the player
     * @return Total number of assists
     */
    public long getTotalAssists(UUID playerId) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT SUM(p.assistsInMatch) FROM PlayerMatchPerformance p WHERE p.player.id = :playerId",
                Long.class);
        query.setParameter("playerId", playerId);
        Long result = query.getSingleResult();
        return result != null ? result : 0;
    }
}
