package com.cookiebuild.cookiedough.service;

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
}