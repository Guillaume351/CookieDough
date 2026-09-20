package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QueueSignalPolicyTest {
    @Test
    void requiresAnOpenNonEmptyQueue() {
        assertFalse(QueueSignalPolicy.shouldPlay(false, 1, 10_000L, -1L));
        assertFalse(QueueSignalPolicy.shouldPlay(true, 0, 10_000L, -1L));
        assertTrue(QueueSignalPolicy.shouldPlay(true, 1, 10_000L, -1L));
    }

    @Test
    void enforcesNineSecondsBetweenSignals() {
        assertFalse(QueueSignalPolicy.shouldPlay(true, 1, 18_999L, 10_000L));
        assertTrue(QueueSignalPolicy.shouldPlay(true, 1, 19_000L, 10_000L));
    }

    @Test
    void clockRollbackCannotBypassTheCadence() {
        assertFalse(QueueSignalPolicy.shouldPlay(true, 1, 9_999L, 10_000L));
    }

    @Test
    void targetsOnlyPlayersWithinSixBlocksOfTheNpc() {
        assertTrue(QueueSignalPolicy.isNearby(0.0));
        assertTrue(QueueSignalPolicy.isNearby(36.0));
        assertFalse(QueueSignalPolicy.isNearby(36.01));
        assertFalse(QueueSignalPolicy.isNearby(Double.NaN));
    }
}
