package com.cookiebuild.cookiedough.service;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
// It's good practice to use a JSON library for gameSpecificMetrics. 
// For this example, direct string manipulation is shown for simplicity, 
// but replace with e.g. Jackson ObjectMapper in a real scenario.
// import com.fasterxml.jackson.databind.ObjectMapper; 
// import com.fasterxml.jackson.core.JsonProcessingException;

public class MatchService {

    private final EntityManager entityManager;
    // private final ObjectMapper objectMapper = new ObjectMapper(); // If using
    // Jackson

    public MatchService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Match startMatch(String gameType, Collection<PlayerData> participants) {
        EntityTransaction transaction = entityManager.getTransaction();
        Match match = new Match(new Date(), gameType);
        if (participants != null) {
            participants.forEach(match.getPlayers()::add); // Add all participants
        }

        try {
            transaction.begin();
            entityManager.persist(match);
            transaction.commit();
            return match;
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            // Log error appropriately
            throw new RuntimeException("Failed to start match: " + e.getMessage(), e);
        }
    }

    public void recordPlayerPerformance(Match match, PlayerData player, int kills, int deaths, int assists,
            Map<String, Object> specificMetrics) {
        EntityTransaction transaction = entityManager.getTransaction();
        PlayerMatchPerformance performance = new PlayerMatchPerformance(match, player);
        performance.setKillsInMatch(kills);
        performance.setDeathsInMatch(deaths);
        performance.setAssistsInMatch(assists);

        if (specificMetrics != null && !specificMetrics.isEmpty()) {
            // Basic JSON-like string conversion. Replace with a proper JSON library.
            String metricsJson = specificMetrics.entrySet().stream()
                    .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue().toString() + "\"")
                    .collect(Collectors.joining(",", "{", "}"));
            performance.setGameSpecificMetrics(metricsJson);
            // Example with Jackson:
            // try {
            // performance.setGameSpecificMetrics(objectMapper.writeValueAsString(specificMetrics));
            // } catch (JsonProcessingException e) {
            // // Handle JSON processing error - log it, maybe set a default error string
            // performance.setGameSpecificMetrics("{\"error\":\"Failed to serialize
            // metrics\"}");
            // }
        }

        try {
            transaction.begin();
            entityManager.persist(performance); // Persist new performance record
                                                // If updating existing, you might fetch then merge.
            // Ensure the performance is added to the match's collection if not already
            // handled by cascade or explicitly.
            // Match should be managed entity if just adding to its collection: match =
            // entityManager.merge(match);
            // match.addPerformance(performance); // This sets both sides of the
            // relationship
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RuntimeException("Failed to record player performance: " + e.getMessage(), e);
        }
    }

    public Match endMatch(Match match, Collection<PlayerData> winners) {
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            Match managedMatch = entityManager.find(Match.class, match.getId());
            if (managedMatch == null) {
                throw new IllegalArgumentException("Match not found or not managed for ending.");
            }
            managedMatch.setEndTime(new Date());
            if (winners != null) {
                winners.forEach(winner -> {
                    // Ensure winners are managed entities if not already
                    PlayerData managedWinner = entityManager.find(PlayerData.class, winner.getId());
                    if (managedWinner != null) {
                        managedMatch.getWinners().add(managedWinner);
                    } else {
                        // Log warning: winner PlayerData not found
                    }
                });
            }
            entityManager.merge(managedMatch);
            transaction.commit();
            return managedMatch;
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new RuntimeException("Failed to end match: " + e.getMessage(), e);
        }
    }
}