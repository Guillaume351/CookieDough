package com.cookiebuild.cookiedough.listener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players whose durable activity cannot currently admit them.
 *
 * <p>The tracker contains no Bukkit state so the retry and backoff lifecycle can
 * be tested deterministically. Player inventories and locations remain owned by
 * the caller while a ticket is pending.</p>
 */
final class PersistentActivityRecovery {
    static final long INITIAL_RETRY_DELAY_MILLIS = 5_000L;
    static final long MAX_RETRY_DELAY_MILLIS = 60_000L;

    record Ticket(UUID playerId, String activityName, int failures, long retryAtMillis) { }

    private final Map<UUID, Ticket> tickets = new ConcurrentHashMap<>();

    /** Returns true only when a new recovery quarantine was created. */
    boolean hold(UUID playerId, String activityName, long nowMillis) {
        if (playerId == null || activityName == null || activityName.isBlank()) {
            throw new IllegalArgumentException("A player and activity name are required");
        }
        Ticket initial = new Ticket(playerId, activityName, 0, nowMillis);
        Ticket previous = tickets.putIfAbsent(playerId, initial);
        if (previous != null && !previous.activityName().equalsIgnoreCase(activityName)) {
            tickets.put(playerId, initial);
        }
        return previous == null;
    }

    List<Ticket> due(long nowMillis) {
        return tickets.values().stream()
                .filter(ticket -> ticket.retryAtMillis() <= nowMillis)
                .toList();
    }

    void rejected(Ticket attempted, long nowMillis) {
        if (attempted == null) return;
        tickets.computeIfPresent(attempted.playerId(), (ignored, current) -> {
            if (!current.activityName().equalsIgnoreCase(attempted.activityName())) return current;
            int failures = Math.min(31, current.failures() + 1);
            int exponent = Math.min(4, Math.max(0, failures - 1));
            long delay = Math.min(MAX_RETRY_DELAY_MILLIS,
                    INITIAL_RETRY_DELAY_MILLIS * (1L << exponent));
            return new Ticket(current.playerId(), current.activityName(), failures, nowMillis + delay);
        });
    }

    void recovered(Ticket attempted) {
        if (attempted != null) {
            tickets.computeIfPresent(attempted.playerId(), (ignored, current) ->
                    current.activityName().equalsIgnoreCase(attempted.activityName()) ? null : current);
        }
    }

    boolean isHolding(UUID playerId) {
        return playerId != null && tickets.containsKey(playerId);
    }

    void remove(UUID playerId) {
        if (playerId != null) tickets.remove(playerId);
    }

    void clear() {
        tickets.clear();
    }
}
