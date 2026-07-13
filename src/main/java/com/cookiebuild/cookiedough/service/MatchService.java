package com.cookiebuild.cookiedough.service;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;
import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/** Match writes use a fresh persistence context per transaction. */
public class MatchService {
    public record Performance(UUID playerId, int kills, int deaths, int assists,
            Map<String, Object> metrics) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    @SuppressWarnings("unused")
    private final EntityManager legacyEntityManager;

    public MatchService(EntityManager entityManager) {
        this.legacyEntityManager = entityManager;
    }

    public Match startMatch(String gameType, Collection<PlayerData> participants) {
        if (gameType == null || gameType.isBlank()) {
            throw new IllegalArgumentException("gameType is required");
        }
        List<UUID> participantIds = participants == null ? List.of() : participants.stream()
                .filter(Objects::nonNull).map(PlayerData::getId).filter(Objects::nonNull).distinct().toList();
        return inTransaction(em -> {
            Match match = new Match(new Date(), gameType);
            for (UUID participantId : participantIds) {
                PlayerData player = em.find(PlayerData.class, participantId);
                if (player == null) {
                    throw new IllegalArgumentException("Participant not found: " + participantId);
                }
                match.getPlayers().add(player);
            }
            em.persist(match);
            em.flush();
            return match;
        });
    }

    public void recordPlayerPerformance(Match match, PlayerData player, int kills, int deaths, int assists,
            Map<String, Object> specificMetrics) {
        requireIds(match, player);
        inTransaction(em -> {
            Match managedMatch = requireMatch(em, match.getId());
            PlayerData managedPlayer = requirePlayer(em, player.getId());
            PlayerMatchPerformance performance = findPerformance(em, match.getId(), player.getId());
            if (performance == null) {
                performance = new PlayerMatchPerformance(managedMatch, managedPlayer);
                em.persist(performance);
            }
            applyPerformance(performance, kills, deaths, assists, specificMetrics);
            return null;
        });
    }

    public Match endMatch(Match match, Collection<PlayerData> winners) {
        if (match == null || match.getId() == null) {
            throw new IllegalArgumentException("Persisted match is required");
        }
        List<UUID> winnerIds = winners == null ? List.of() : winners.stream()
                .filter(Objects::nonNull).map(PlayerData::getId).filter(Objects::nonNull).distinct().toList();
        return inTransaction(em -> {
            Match managedMatch = requireMatch(em, match.getId());
            finishMatch(em, managedMatch, winnerIds);
            return managedMatch;
        });
    }

    /** Stores every performance and the final result in one transaction. */
    public Match completeMatch(Match match, Collection<PlayerData> winners,
            Collection<Performance> performances) {
        if (match == null || match.getId() == null) {
            throw new IllegalArgumentException("Persisted match is required");
        }
        List<UUID> winnerIds = winners == null ? List.of() : winners.stream()
                .filter(Objects::nonNull).map(PlayerData::getId).filter(Objects::nonNull).distinct().toList();
        List<Performance> results = performances == null ? List.of() : List.copyOf(performances);
        return inTransaction(em -> {
            Match managedMatch = requireMatch(em, match.getId());
            for (Performance result : results) {
                PlayerData player = requirePlayer(em, result.playerId());
                PlayerMatchPerformance performance = findPerformance(em, match.getId(), result.playerId());
                if (performance == null) {
                    performance = new PlayerMatchPerformance(managedMatch, player);
                    em.persist(performance);
                }
                applyPerformance(performance, result.kills(), result.deaths(), result.assists(), result.metrics());
            }
            finishMatch(em, managedMatch, winnerIds);
            return managedMatch;
        });
    }

    private void finishMatch(EntityManager em, Match match, Collection<UUID> winnerIds) {
        match.setEndTime(new Date());
        match.getWinners().clear();
        for (UUID winnerId : winnerIds) {
            match.getWinners().add(requirePlayer(em, winnerId));
        }
    }

    private void applyPerformance(PlayerMatchPerformance performance, int kills, int deaths, int assists,
            Map<String, Object> metrics) {
        performance.setKillsInMatch(Math.max(0, kills));
        performance.setDeathsInMatch(Math.max(0, deaths));
        performance.setAssistsInMatch(Math.max(0, assists));
        try {
            performance.setGameSpecificMetrics(metrics == null || metrics.isEmpty() ? null : JSON.writeValueAsString(metrics));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid performance metrics", error);
        }
    }

    private PlayerMatchPerformance findPerformance(EntityManager em, UUID matchId, UUID playerId) {
        return em.createQuery("SELECT p FROM PlayerMatchPerformance p WHERE p.match.id = :matchId "
                        + "AND p.player.id = :playerId", PlayerMatchPerformance.class)
                .setParameter("matchId", matchId).setParameter("playerId", playerId)
                .getResultStream().findFirst().orElse(null);
    }

    private Match requireMatch(EntityManager em, UUID matchId) {
        Match match = em.find(Match.class, matchId);
        if (match == null) {
            throw new IllegalArgumentException("Match not found: " + matchId);
        }
        return match;
    }

    private PlayerData requirePlayer(EntityManager em, UUID playerId) {
        PlayerData player = em.find(PlayerData.class, playerId);
        if (player == null) {
            throw new IllegalArgumentException("Player not found: " + playerId);
        }
        return player;
    }

    private void requireIds(Match match, PlayerData player) {
        if (match == null || match.getId() == null || player == null || player.getId() == null) {
            throw new IllegalArgumentException("Persisted match and player are required");
        }
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
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw new RuntimeException("Match persistence failed: " + error.getMessage(), error);
            }
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T apply(EntityManager entityManager);
    }
}
