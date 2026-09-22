package com.cookiebuild.cookiedough.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.model.PlayerSession;

class PlayerSessionRecoveryServiceTest {
    @Test
    void closesOnlyOpenSessionsAndPreservesCrashEvidence() {
        Date restart = new Date(10_000L);
        PlayerSession stale = new PlayerSession();
        stale.setStartTime(new Date(2_000L));
        stale.setDuration(3_000L);
        stale.setServerCrash(true);
        PlayerSession alreadyClosed = new PlayerSession();
        alreadyClosed.setStartTime(new Date(1_000L));
        alreadyClosed.setEndTime(new Date(3_000L));
        alreadyClosed.setDuration(2_000L);

        int recovered = PlayerSessionRecoveryService.finalizeSessions(
                List.of(stale, alreadyClosed), restart);

        assertEquals(1, recovered);
        assertEquals(new Date(5_000L), stale.getEndTime());
        assertEquals(3_000L, stale.getDuration());
        assertTrue(stale.isServerCrash());
        assertEquals(new Date(3_000L), alreadyClosed.getEndTime());
        assertEquals(2_000L, alreadyClosed.getDuration());
    }

    @Test
    void neverStoresNegativeDurationsWhenClocksMoveBackwards() {
        assertEquals(0L, PlayerSessionRecoveryService.durationMillis(
                new Date(5_000L), new Date(4_000L)));
    }

    @Test
    void legacySessionWithoutCheckpointDoesNotCountServerDowntime() {
        PlayerSession stale = new PlayerSession();
        stale.setStartTime(new Date(2_000L));

        PlayerSessionRecoveryService.finalizeSessions(List.of(stale), new Date(20_000L));

        assertEquals(new Date(2_000L), stale.getEndTime());
        assertEquals(0L, stale.getDuration());
        assertTrue(stale.isServerCrash());
    }

    @Test
    void corruptFutureCheckpointIsCappedAtRecoveryTime() {
        PlayerSession stale = new PlayerSession();
        stale.setStartTime(new Date(2_000L));
        stale.setDuration(50_000L);

        PlayerSessionRecoveryService.finalizeSessions(List.of(stale), new Date(10_000L));

        assertEquals(new Date(10_000L), stale.getEndTime());
        assertEquals(8_000L, stale.getDuration());
    }
}
