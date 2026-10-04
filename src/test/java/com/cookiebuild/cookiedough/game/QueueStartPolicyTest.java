package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class QueueStartPolicyTest {
    @Test
    void disabledHookKeepsTheConfiguredMinimum() {
        assertEquals(2, QueueStartPolicy.effectiveMinimumPlayers(2, 1, 600, QueueStartPolicy.SOLO_START_DISABLED));
        assertEquals(-1L, QueueStartPolicy.secondsUntilSoloStart(1, 600, QueueStartPolicy.SOLO_START_DISABLED));
    }

    @Test
    void aLonePlayerMayStartOnlyAfterTheModeDelay() {
        assertEquals(2, QueueStartPolicy.effectiveMinimumPlayers(2, 1, 14, 15));
        assertEquals(1, QueueStartPolicy.effectiveMinimumPlayers(2, 1, 15, 15));
        assertEquals(1, QueueStartPolicy.effectiveMinimumPlayers(4, 1, 90, 0));
        assertEquals(5L, QueueStartPolicy.secondsUntilSoloStart(1, 10, 15));
        assertEquals(0L, QueueStartPolicy.secondsUntilSoloStart(1, 40, 15));
    }

    @Test
    void soloStartAppliesToExactlyOnePlayer() {
        assertEquals(2, QueueStartPolicy.effectiveMinimumPlayers(2, 0, 60, 15));
        assertEquals(4, QueueStartPolicy.effectiveMinimumPlayers(4, 2, 60, 15));
        assertEquals(-1L, QueueStartPolicy.secondsUntilSoloStart(2, 60, 15));
    }

    @Test
    void neverRaisesAnAlreadySoloMinimum() {
        assertEquals(1, QueueStartPolicy.effectiveMinimumPlayers(1, 1, 60, 15));
    }

    @Test
    void theSoloMatchEtaIncludesTheCountdownThatFollowsTheSoloDelay() {
        assertEquals(25, QueueStartPolicy.secondsUntilSoloMatch(1, 0, 15, 10));
        assertEquals(33, QueueStartPolicy.secondsUntilSoloMatch(1, 12, 15, 30));
        assertEquals(10, QueueStartPolicy.secondsUntilSoloMatch(1, 40, 15, 10));
        assertEquals(-1, QueueStartPolicy.secondsUntilSoloMatch(2, 0, 15, 10));
        assertEquals(-1, QueueStartPolicy.secondsUntilSoloMatch(1, 0, QueueStartPolicy.SOLO_START_DISABLED, 10));
    }
}
