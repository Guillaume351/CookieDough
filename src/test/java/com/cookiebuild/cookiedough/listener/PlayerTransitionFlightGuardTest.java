package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlayerTransitionFlightGuardTest {
    @Test
    void protectsBeforeTeleportAndRevokesAfterLanding() {
        ManualScheduler scheduler = new ManualScheduler();
        PlayerTransitionFlightGuard guard = new PlayerTransitionFlightGuard(scheduler);
        FakeSubject player = new FakeSubject();

        assertTrue(guard.teleport(player, () -> {
            player.trace.add("teleport");
            return true;
        }));
        assertEquals(List.of("flying:false", "allow:true", "teleport"), player.trace);
        player.onGround = true;
        for (int tick = 0; tick < PlayerTransitionFlightGuard.MINIMUM_LANDING_GRACE_TICKS; tick++) {
            scheduler.runNext();
        }

        assertFalse(guard.isProtected(player.id()));
        assertFalse(player.allowFlight);
    }

    @Test
    void loadingTimeoutRunsSafeFallbackBeforeRevoking() {
        ManualScheduler scheduler = new ManualScheduler();
        PlayerTransitionFlightGuard guard = new PlayerTransitionFlightGuard(scheduler);
        FakeSubject player = new FakeSubject();

        guard.protectLoading(player, () -> player.trace.add("safe-fallback"));
        scheduler.runNext();

        assertEquals(List.of("flying:false", "allow:true", "safe-fallback",
                "flying:false", "allow:false"), player.trace);
        assertFalse(guard.isProtected(player.id()));
    }

    @Test
    void creativeDestinationKeepsModeOwnedFlightPermission() {
        ManualScheduler scheduler = new ManualScheduler();
        PlayerTransitionFlightGuard guard = new PlayerTransitionFlightGuard(scheduler);
        FakeSubject player = new FakeSubject();
        player.retainsFlight = true;

        guard.teleport(player, () -> true);
        player.onGround = true;
        for (int tick = 0; tick < PlayerTransitionFlightGuard.MINIMUM_LANDING_GRACE_TICKS; tick++) {
            scheduler.runNext();
        }

        assertTrue(player.allowFlight);
        assertFalse(guard.isProtected(player.id()));
    }

    @Test
    void reconnectRestoresTheCapturedFlightStateOnlyAfterLanding() {
        ManualScheduler scheduler = new ManualScheduler();
        PlayerTransitionFlightGuard guard = new PlayerTransitionFlightGuard(scheduler);
        FakeSubject player = new FakeSubject();

        guard.protectLanding(player, false, false);
        assertTrue(player.allowFlight);
        player.onGround = true;
        for (int tick = 0; tick < PlayerTransitionFlightGuard.MINIMUM_LANDING_GRACE_TICKS; tick++) {
            scheduler.runNext();
        }

        assertFalse(player.allowFlight);
        assertFalse(guard.isProtected(player.id()));
    }

    @Test
    void inheritedOnGroundFlagCannotCollapseTheMinimumGraceWindow() {
        ManualScheduler scheduler = new ManualScheduler();
        PlayerTransitionFlightGuard guard = new PlayerTransitionFlightGuard(scheduler);
        FakeSubject player = new FakeSubject();
        player.onGround = true;

        guard.teleport(player, () -> true);
        for (int tick = 1; tick < PlayerTransitionFlightGuard.MINIMUM_LANDING_GRACE_TICKS; tick++) {
            scheduler.runNext();
            assertTrue(guard.isProtected(player.id()));
        }
        scheduler.runNext();

        assertFalse(guard.isProtected(player.id()));
        assertFalse(player.allowFlight);
    }

    private static final class ManualScheduler implements PlayerTransitionFlightGuard.Scheduler {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void later(Runnable action, long delayTicks) { tasks.addLast(action); }
        void runNext() { tasks.removeFirst().run(); }
    }

    private static final class FakeSubject implements PlayerTransitionFlightGuard.FlightSubject {
        private final UUID id = UUID.randomUUID();
        private final List<String> trace = new ArrayList<>();
        private boolean online = true;
        private boolean onGround;
        private boolean retainsFlight;
        private boolean allowFlight;
        @Override public UUID id() { return id; }
        @Override public boolean online() { return online; }
        @Override public boolean onGround() { return onGround; }
        @Override public boolean retainsFlight() { return retainsFlight; }
        @Override public void flying(boolean value) { trace.add("flying:" + value); }
        @Override public void allowFlight(boolean value) {
            allowFlight = value;
            trace.add("allow:" + value);
        }
    }
}
