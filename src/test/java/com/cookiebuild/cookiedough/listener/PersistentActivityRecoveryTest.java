package com.cookiebuild.cookiedough.listener;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PersistentActivityRecoveryTest {
    @Test
    void asynchronousRecoveryMarshalsEveryBukkitMutationAndRechecksTheLiveSession() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/listener/PlayerWrapperListener.java"));
        int method = source.indexOf("public static void recoverPersistentActivity");
        int nextMethod = source.indexOf("public static Date getPlayerLoginTime", method);
        String recovery = source.substring(method, nextMethod);
        assertTrue(recovery.contains("Bukkit.isPrimaryThread()"));
        assertTrue(recovery.contains("Bukkit.getScheduler().runTask"));
        assertTrue(recovery.contains("activePlayerSessions.get(playerId)"));
        assertTrue(recovery.contains("readyPlayers.contains(playerId)"));
        assertTrue(recovery.contains("player.isOnline()"));
        assertTrue(recovery.indexOf("holdPersistentActivity") > recovery.indexOf("Runnable recovery"));
    }

    @Test
    void quarantinesWithoutDiscardingTheDurableDestinationAndRecoversLater() {
        PersistentActivityRecovery recovery = new PersistentActivityRecovery();
        UUID playerId = UUID.randomUUID();

        assertTrue(recovery.hold(playerId, "Skyblock", 1_000L));
        assertTrue(recovery.isHolding(playerId));
        PersistentActivityRecovery.Ticket firstAttempt = recovery.due(1_000L).getFirst();
        assertEquals("Skyblock", firstAttempt.activityName());

        recovery.rejected(firstAttempt, 1_000L);
        assertTrue(recovery.due(5_999L).isEmpty());
        PersistentActivityRecovery.Ticket retry = recovery.due(6_000L).getFirst();
        recovery.recovered(retry);

        assertFalse(recovery.isHolding(playerId));
    }

    @Test
    void rejectedAdmissionsUseBoundedExponentialBackoff() {
        PersistentActivityRecovery recovery = new PersistentActivityRecovery();
        UUID playerId = UUID.randomUUID();
        recovery.hold(playerId, "Skyblock", 0L);

        long now = 0L;
        long[] expectedDelays = {5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L};
        for (long expectedDelay : expectedDelays) {
            PersistentActivityRecovery.Ticket attempt = recovery.due(now).getFirst();
            recovery.rejected(attempt, now);
            now += expectedDelay;
            assertEquals(now, recovery.due(now).getFirst().retryAtMillis());
        }
    }

    @Test
    void joinFlightGuardHandsOffBeforeTheActivityInstallsItsOwnGuard() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/listener/PlayerWrapperListener.java"));
        int method = source.indexOf("private boolean attemptPersistentResume");
        int nextMethod = source.indexOf("private void holdPersistentActivity", method);
        String resume = source.substring(method, nextMethod);

        assertTrue(resume.indexOf("getPlayerTransitionFlightGuard().abandon(player)")
                < resume.indexOf("ActivityRegistry.enter(activityName, cookiePlayer)"));
    }
}
