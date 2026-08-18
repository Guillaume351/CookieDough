package com.cookiebuild.cookiedough.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    void rechecksProgressionAfterSerializingConcurrentCreation() {
        UUID playerId = UUID.randomUUID();
        MinigameProgression concurrentWinner = new MinigameProgression(playerId,
                MinigameProgressionService.SKYWARS);
        AtomicInteger progressionLookups = new AtomicInteger();
        AtomicReference<LockModeType> requestedLock = new AtomicReference<>();
        AtomicBoolean persisted = new AtomicBoolean();
        EntityManager entityManager = fakeEntityManager((entityType, lockMode) -> {
            if (entityType == PlayerData.class) {
                requestedLock.set(lockMode);
                return new PlayerData();
            }
            return progressionLookups.getAndIncrement() == 0 ? null : concurrentWinner;
        }, ignored -> persisted.set(true));

        MinigameProgression result = new MinigameProgressionService(null).findOrCreate(
                entityManager, playerId, MinigameProgressionService.SKYWARS);

        assertSame(concurrentWinner, result);
        assertEquals(2, progressionLookups.get());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, requestedLock.get());
        assertFalse(persisted.get());
    }

    @Test
    void createsProgressionWhenLockedRecheckStillFindsNothing() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger progressionLookups = new AtomicInteger();
        AtomicReference<LockModeType> requestedLock = new AtomicReference<>();
        AtomicReference<Object> persisted = new AtomicReference<>();
        EntityManager entityManager = fakeEntityManager((entityType, lockMode) -> {
            if (entityType == PlayerData.class) {
                requestedLock.set(lockMode);
                return new PlayerData();
            }
            progressionLookups.incrementAndGet();
            return null;
        }, persisted::set);

        MinigameProgression result = new MinigameProgressionService(null).findOrCreate(
                entityManager, playerId, MinigameProgressionService.SKYWARS);

        assertSame(result, persisted.get());
        assertEquals(playerId, result.getPlayerId());
        assertEquals(MinigameProgressionService.SKYWARS, result.getMinigame());
        assertEquals(2, progressionLookups.get());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, requestedLock.get());
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
