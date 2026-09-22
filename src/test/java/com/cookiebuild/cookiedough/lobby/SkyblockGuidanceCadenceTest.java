package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SkyblockGuidanceCadenceTest {
    @Test
    void delaysBoundsAndCoolsDownEachTrail() {
        SkyblockGuidanceCadence cadence = new SkyblockGuidanceCadence(100L, 50L, 200L);
        UUID player = UUID.randomUUID();

        assertEquals(SkyblockGuidanceCadence.Decision.NONE, cadence.evaluate(player, true, 0L));
        assertEquals(SkyblockGuidanceCadence.Decision.NONE, cadence.evaluate(player, true, 99L));
        assertEquals(SkyblockGuidanceCadence.Decision.START, cadence.evaluate(player, true, 100L));
        assertEquals(SkyblockGuidanceCadence.Decision.ACTIVE, cadence.evaluate(player, true, 149L));
        assertEquals(SkyblockGuidanceCadence.Decision.NONE, cadence.evaluate(player, true, 150L));
        assertEquals(SkyblockGuidanceCadence.Decision.NONE, cadence.evaluate(player, true, 349L));
        assertEquals(SkyblockGuidanceCadence.Decision.START, cadence.evaluate(player, true, 350L));
    }

    @Test
    void stopsImmediatelyAndPreservesCooldownAcrossEligibilityChanges() {
        SkyblockGuidanceCadence cadence = new SkyblockGuidanceCadence(100L, 50L, 200L);
        UUID player = UUID.randomUUID();

        cadence.evaluate(player, true, 0L);
        assertEquals(SkyblockGuidanceCadence.Decision.START, cadence.evaluate(player, true, 100L));
        assertEquals(SkyblockGuidanceCadence.Decision.NONE, cadence.evaluate(player, false, 110L));
        assertEquals(SkyblockGuidanceCadence.Decision.NONE, cadence.evaluate(player, true, 200L));
        assertEquals(SkyblockGuidanceCadence.Decision.NONE, cadence.evaluate(player, true, 300L));
        assertEquals(SkyblockGuidanceCadence.Decision.START, cadence.evaluate(player, true, 350L));
    }
}
