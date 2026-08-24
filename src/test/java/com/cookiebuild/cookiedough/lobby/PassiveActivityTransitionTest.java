package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PassiveActivityTransitionTest {
    @Test
    void unavailableDestinationNeverLeavesTheSource() {
        AtomicInteger leaves = new AtomicInteger();
        AtomicInteger restores = new AtomicInteger();

        assertFalse(PassiveActivityTransition.execute(() -> false,
                () -> { leaves.incrementAndGet(); return true; }, () -> true, restores::incrementAndGet));
        assertEquals(0, leaves.get());
        assertEquals(0, restores.get());
    }

    @Test
    void failedTargetAdmissionRestoresExactlyOnce() {
        AtomicInteger restores = new AtomicInteger();

        assertFalse(PassiveActivityTransition.execute(() -> true, () -> true,
                () -> false, restores::incrementAndGet));
        assertEquals(1, restores.get());
    }

    @Test
    void successfulAdmissionDoesNotRestoreTheOldActivity() {
        AtomicInteger restores = new AtomicInteger();

        assertTrue(PassiveActivityTransition.execute(() -> true, () -> true,
                () -> true, restores::incrementAndGet));
        assertEquals(0, restores.get());
    }
}
