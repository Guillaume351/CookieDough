package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LoginRallyTrackerTest {
    @Test
    void offlineTargetCancelsBeforeExpiry() {
        LoginRallyTracker tracker = new LoginRallyTracker();
        UUID target = UUID.randomUUID();
        UUID outbox = UUID.randomUUID();
        tracker.track(target, outbox, 303_000);

        List<LoginRallyTracker.Pending> cancellations = tracker.observe(1_000, ignored -> false);

        assertEquals(List.of(outbox), cancellations.stream()
                .map(LoginRallyTracker.Pending::outboxId).toList());
        assertTrue(tracker.allPending().isEmpty());
    }

    @Test
    void secondPlayerCancelsThroughTheEligibilityPredicate() {
        LoginRallyTracker tracker = new LoginRallyTracker();
        UUID target = UUID.randomUUID();
        UUID outbox = UUID.randomUUID();
        tracker.track(target, outbox, 303_000);
        int onlinePlayers = 2;

        List<LoginRallyTracker.Pending> cancellations = tracker.observe(
                1_000, playerId -> playerId.equals(target) && onlinePlayers <= 1);

        assertEquals(1, cancellations.size());
        assertEquals(target, cancellations.getFirst().targetPlayerId());
    }

    @Test
    void availableOutboxRemainsTrackedUntilExpiry() {
        LoginRallyTracker tracker = new LoginRallyTracker();
        UUID target = UUID.randomUUID();
        UUID outbox = UUID.randomUUID();
        tracker.track(target, outbox, 303_000);

        assertTrue(tracker.observe(3_000, ignored -> true).isEmpty());
        assertEquals(List.of(outbox), tracker.allPending().stream()
                .map(LoginRallyTracker.Pending::outboxId).toList());
    }

    @Test
    void expiryRemovesAnEligibleRallyWithoutCancellingIt() {
        LoginRallyTracker tracker = new LoginRallyTracker();
        tracker.track(UUID.randomUUID(), UUID.randomUUID(), 303_000);

        assertTrue(tracker.observe(303_000, ignored -> true).isEmpty());
        assertTrue(tracker.allPending().isEmpty());
    }

    @Test
    void ineligibleTargetAtTheExactExpiryBoundaryStillCancels() {
        LoginRallyTracker tracker = new LoginRallyTracker();
        UUID outbox = UUID.randomUUID();
        tracker.track(UUID.randomUUID(), outbox, 303_000);

        List<LoginRallyTracker.Pending> cancellations = tracker.observe(303_000, ignored -> false);

        assertEquals(List.of(outbox), cancellations.stream()
                .map(LoginRallyTracker.Pending::outboxId).toList());
        assertTrue(tracker.allPending().isEmpty());
    }

    @Test
    void shutdownCanExposeEveryPendingOutbox() {
        LoginRallyTracker tracker = new LoginRallyTracker();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        tracker.track(UUID.randomUUID(), first, 303_000);
        tracker.track(UUID.randomUUID(), second, 304_000);

        assertEquals(Set.of(first, second), tracker.allPending().stream()
                .map(LoginRallyTracker.Pending::outboxId)
                .collect(java.util.stream.Collectors.toSet()));
    }
}
