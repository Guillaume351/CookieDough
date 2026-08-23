package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PersistentActivityRecoveryTest {
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
}
