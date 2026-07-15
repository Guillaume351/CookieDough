package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class StandbyRefillGateTest {
    @Test
    void requiresAContinuousQuietWindow() {
        AtomicLong now = new AtomicLong();
        StandbyRefillGate gate = new StandbyRefillGate(Duration.ofSeconds(30), now::get);

        assertFalse(gate.canRefill(false, false));
        now.set(Duration.ofSeconds(29).toNanos());
        assertFalse(gate.canRefill(false, false));
        now.set(Duration.ofSeconds(30).toNanos());
        assertTrue(gate.canRefill(false, false));

        assertFalse(gate.canRefill(true, false));
        now.set(Duration.ofSeconds(60).toNanos());
        assertFalse(gate.canRefill(false, false));
        now.set(Duration.ofSeconds(90).toNanos());
        assertTrue(gate.canRefill(false, false));
    }

    @Test
    void activeGameplayAlsoResetsTheQuietWindow() {
        AtomicLong now = new AtomicLong();
        StandbyRefillGate gate = new StandbyRefillGate(Duration.ofSeconds(1), now::get);

        assertFalse(gate.canRefill(false, true));
        assertFalse(gate.canRefill(false, false));
        now.set(Duration.ofSeconds(1).toNanos());
        assertTrue(gate.canRefill(false, false));
    }
}
