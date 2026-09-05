package com.cookiebuild.cookiedough.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.cosmetics.VictoryEffectDispatcher;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.PlayerData;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

class MatchServiceVictoryEffectTest {
    @Test
    void winnerEffectIsDispatchedOnlyAfterSuccessfulCommit() {
        UUID matchId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        Match match = match(matchId);
        PlayerData winner = player(winnerId);
        List<String> lifecycle = new ArrayList<>();
        EntityTransaction transaction = transaction(lifecycle, false);
        EntityManager entityManager = entityManager(transaction, match, winner, lifecycle);
        VictoryEffectDispatcher dispatcher = id -> lifecycle.add("effect:" + id);

        new MatchService(() -> entityManager, dispatcher)
                .completeMatchByWinnerIds(match, List.of(winnerId), List.of());

        assertEquals(List.of("begin", "commit", "close", "effect:" + winnerId), lifecycle);
    }

    @Test
    void failedCommitNeverDispatchesWinnerEffect() {
        UUID matchId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        Match match = match(matchId);
        PlayerData winner = player(winnerId);
        List<String> lifecycle = new ArrayList<>();
        EntityTransaction transaction = transaction(lifecycle, true);
        EntityManager entityManager = entityManager(transaction, match, winner, lifecycle);
        VictoryEffectDispatcher dispatcher = id -> lifecycle.add("effect:" + id);

        assertThrows(RuntimeException.class, () -> new MatchService(() -> entityManager, dispatcher)
                .completeMatchByWinnerIds(match, List.of(winnerId), List.of()));
        assertEquals(List.of("begin", "commit", "rollback", "close"), lifecycle);
    }

    @Test
    void failedVisualDeliveryDoesNotFailAnAlreadyCommittedMatch() {
        Match match = match(UUID.randomUUID());
        UUID winnerId = UUID.randomUUID();
        List<String> lifecycle = new ArrayList<>();
        EntityManager entityManager = entityManager(transaction(lifecycle, false), match,
                player(winnerId), lifecycle);
        Match result = new MatchService(() -> entityManager, id -> {
            throw new IllegalStateException("scheduler stopped");
        }).completeMatchByWinnerIds(match, List.of(winnerId), List.of());
        assertEquals(match, result);
        assertEquals(List.of("begin", "commit", "close"), lifecycle);
    }

    private static EntityTransaction transaction(List<String> lifecycle, boolean failCommit) {
        AtomicBoolean active = new AtomicBoolean();
        return (EntityTransaction) Proxy.newProxyInstance(
                MatchServiceVictoryEffectTest.class.getClassLoader(),
                new Class<?>[] { EntityTransaction.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "begin" -> {
                        active.set(true);
                        lifecycle.add("begin");
                        yield null;
                    }
                    case "commit" -> {
                        lifecycle.add("commit");
                        if (failCommit) throw new IllegalStateException("commit failed");
                        active.set(false);
                        yield null;
                    }
                    case "rollback" -> {
                        active.set(false);
                        lifecycle.add("rollback");
                        yield null;
                    }
                    case "isActive" -> active.get();
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static EntityManager entityManager(
            EntityTransaction transaction, Match match, PlayerData winner, List<String> lifecycle) {
        return (EntityManager) Proxy.newProxyInstance(
                MatchServiceVictoryEffectTest.class.getClassLoader(),
                new Class<?>[] { EntityManager.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTransaction" -> transaction;
                    case "find" -> args[0] == Match.class ? match : winner;
                    case "close" -> {
                        lifecycle.add("close");
                        yield null;
                    }
                    case "isOpen" -> true;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Match match(UUID id) {
        Match match = new Match(new java.util.Date(), "test");
        match.setId(id);
        return match;
    }

    private static PlayerData player(UUID id) {
        PlayerData player = new PlayerData();
        player.setId(id);
        return player;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        return 0d;
    }
}
