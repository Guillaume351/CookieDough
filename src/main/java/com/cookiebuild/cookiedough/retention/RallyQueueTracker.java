package com.cookiebuild.cookiedough.retention;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Main-thread-only state machine for sustained underfilled queues and cancellation. */
final class RallyQueueTracker {
    static final Duration AUTOMATIC_WAIT = Duration.ofSeconds(90);
    static final Duration RETRY_DELAY = Duration.ofMinutes(1);

    record QueueState(UUID gameId, boolean open, int queuedCount, int minimumPlayers) {
        boolean underfilled() {
            return open && queuedCount > 0 && queuedCount < minimumPlayers;
        }
    }

    record Pending(UUID gameId, UUID outboxId, long releaseAtMillis) {
    }

    record Observation(List<UUID> automaticCandidates, List<Pending> cancellations) {
    }

    private final Map<UUID, Long> underfilledSince = new HashMap<>();
    private final Map<UUID, Long> nextAutomaticAttempt = new HashMap<>();
    private final Map<UUID, Pending> pendingByGame = new HashMap<>();

    Observation observe(List<QueueState> states, long nowMillis) {
        Map<UUID, QueueState> current = new HashMap<>();
        states.forEach(state -> current.put(state.gameId(), state));
        Set<UUID> known = new HashSet<>(underfilledSince.keySet());
        known.addAll(pendingByGame.keySet());

        List<Pending> cancellations = new ArrayList<>();
        for (UUID gameId : known) {
            QueueState state = current.get(gameId);
            if (state == null || !state.underfilled()) {
                underfilledSince.remove(gameId);
                nextAutomaticAttempt.remove(gameId);
                Pending pending = pendingByGame.remove(gameId);
                if (pending != null) {
                    cancellations.add(pending);
                }
            }
        }

        List<UUID> candidates = new ArrayList<>();
        for (QueueState state : states) {
            if (!state.underfilled()) {
                continue;
            }
            long since = underfilledSince.computeIfAbsent(state.gameId(), ignored -> nowMillis);
            boolean waitedLongEnough = nowMillis - since >= AUTOMATIC_WAIT.toMillis();
            boolean retryReady = nowMillis >= nextAutomaticAttempt.getOrDefault(state.gameId(), 0L);
            if (waitedLongEnough && retryReady && !pendingByGame.containsKey(state.gameId())) {
                candidates.add(state.gameId());
                nextAutomaticAttempt.put(state.gameId(), nowMillis + RETRY_DELAY.toMillis());
            }
        }
        return new Observation(List.copyOf(candidates), List.copyOf(cancellations));
    }

    boolean hasPending(UUID gameId) {
        return pendingByGame.containsKey(gameId);
    }

    void markScheduled(UUID gameId, UUID outboxId, long releaseAtMillis, long nextAutomaticAtMillis) {
        pendingByGame.put(gameId, new Pending(gameId, outboxId, releaseAtMillis));
        nextAutomaticAttempt.put(gameId, nextAutomaticAtMillis);
    }

    void suppressAutomaticUntil(UUID gameId, long timestampMillis) {
        nextAutomaticAttempt.merge(gameId, timestampMillis, Math::max);
    }

    List<Pending> releaseReady(long nowMillis) {
        List<Pending> released = pendingByGame.values().stream()
                .filter(pending -> pending.releaseAtMillis() <= nowMillis)
                .toList();
        released.forEach(pending -> pendingByGame.remove(pending.gameId(), pending));
        return released;
    }

    Pending removePending(UUID gameId) {
        return pendingByGame.remove(gameId);
    }

    List<Pending> allPending() {
        return List.copyOf(pendingByGame.values());
    }
}
