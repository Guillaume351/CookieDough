package com.cookiebuild.cookiedough.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.model.MinigameProgression;
import com.cookiebuild.cookiedough.model.PlayerData;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

class MinigameProgressionServiceTest {
    @Test
    void recognizesBedWarsProgressionForMatchAndGoalRewards() {
        assertEquals(MinigameProgressionService.BEDWARS,
                MinigameProgressionService.supportedMinigameKey("BedWars"));
    }
    @Test
    void recognizesTurfWarsProgressionForMatchAndGoalRewards() {
        assertEquals(MinigameProgressionService.TURFWARS,
                MinigameProgressionService.supportedMinigameKey("TurfWars"));
        assertEquals(MinigameProgressionService.TURFWARS,
                MinigameProgressionService.supportedMinigameKey("turfwars"));
    }

    @Test
    void goalRewardsFailClosedForUnknownGames() {
        assertFalse(new MinigameProgressionService(null).claimGoalReward(
                UUID.randomUUID(), "unknown-game", 20, 15, "daily-test"));
    }

    @Test
    void locksPlayerBeforeProgressionAndReturnsExistingRow() {
        UUID playerId = UUID.randomUUID();
        MinigameProgression concurrentWinner = new MinigameProgression(playerId,
                MinigameProgressionService.SKYWARS);
        List<String> lookups = new ArrayList<>();
        AtomicBoolean persisted = new AtomicBoolean();
        EntityManager entityManager = fakeEntityManager((entityType, lockMode) -> {
            lookups.add(entityType.getSimpleName() + ":" + lockMode);
            if (entityType == PlayerData.class) {
                return new PlayerData();
            }
            return concurrentWinner;
        }, ignored -> persisted.set(true));

        MinigameProgression result = new MinigameProgressionService(null).findOrCreate(
                entityManager, playerId, MinigameProgressionService.SKYWARS);

        assertSame(concurrentWinner, result);
        assertEquals(List.of(
                "PlayerData:PESSIMISTIC_WRITE",
                "MinigameProgression:PESSIMISTIC_WRITE"), lookups);
        assertFalse(persisted.get());
    }

    @Test
    void createsProgressionWhenLockedRecheckStillFindsNothing() {
        UUID playerId = UUID.randomUUID();
        List<String> lookups = new ArrayList<>();
        AtomicReference<Object> persisted = new AtomicReference<>();
        EntityManager entityManager = fakeEntityManager((entityType, lockMode) -> {
            lookups.add(entityType.getSimpleName() + ":" + lockMode);
            if (entityType == PlayerData.class) {
                return new PlayerData();
            }
            return null;
        }, persisted::set);

        MinigameProgression result = new MinigameProgressionService(null).findOrCreate(
                entityManager, playerId, MinigameProgressionService.SKYWARS);

        assertSame(result, persisted.get());
        assertEquals(playerId, result.getPlayerId());
        assertEquals(MinigameProgressionService.SKYWARS, result.getMinigame());
        assertEquals(List.of(
                "PlayerData:PESSIMISTIC_WRITE",
                "MinigameProgression:PESSIMISTIC_WRITE"), lookups);
    }

    @Test
    void rejectsProgressionCreationBeforePlayerDataExists() {
        UUID playerId = UUID.randomUUID();
        AtomicBoolean persisted = new AtomicBoolean();
        EntityManager entityManager = fakeEntityManager((entityType, lockMode) -> null,
                ignored -> persisted.set(true));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new MinigameProgressionService(null).findOrCreate(
                        entityManager, playerId, MinigameProgressionService.SKYWARS));

        assertEquals("Cannot initialize progression before player data: " + playerId, error.getMessage());
        assertFalse(persisted.get());
    }

    private static EntityManager fakeEntityManager(EntityLookup lookup,
            java.util.function.Consumer<Object> persist) {
        return (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[] {EntityManager.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("find")) {
                        Class<?> entityType = (Class<?>) arguments[0];
                        LockModeType lockMode = arguments.length >= 3 && arguments[2] instanceof LockModeType mode
                                ? mode : null;
                        return lookup.find(entityType, lockMode);
                    }
                    if (method.getName().equals("persist")) {
                        persist.accept(arguments[0]);
                        return null;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "FakeEntityManager";
                            default -> throw new UnsupportedOperationException(method.toString());
                        };
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    @FunctionalInterface
    private interface EntityLookup {
        Object find(Class<?> entityType, LockModeType lockMode);
    }
}
