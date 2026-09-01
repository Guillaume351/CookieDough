package com.cookiebuild.cookiedough.retention;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Main-thread-only lifecycle for login rallies through their complete response window. */
final class LoginRallyTracker {
    record Pending(UUID targetPlayerId, UUID outboxId, long expiresAtMillis) {
        Pending {
            if (targetPlayerId == null || outboxId == null) {
                throw new IllegalArgumentException("Login rally target and outbox are required");
            }
        }
    }

    private final Map<UUID, Pending> pendingByOutbox = new HashMap<>();

    void track(UUID targetPlayerId, UUID outboxId, long expiresAtMillis) {
        pendingByOutbox.put(outboxId, new Pending(targetPlayerId, outboxId, expiresAtMillis));
    }

    List<Pending> observe(long nowMillis, Predicate<UUID> targetEligible) {
        List<Pending> cancellations = pendingByOutbox.values().stream()
                .filter(pending -> !targetEligible.test(pending.targetPlayerId()))
                .toList();
        cancellations.forEach(pending -> pendingByOutbox.remove(pending.outboxId(), pending));

        List<Pending> expired = pendingByOutbox.values().stream()
                .filter(pending -> pending.expiresAtMillis() <= nowMillis)
                .toList();
        expired.forEach(pending -> pendingByOutbox.remove(pending.outboxId(), pending));
        return cancellations;
    }

    List<Pending> allPending() {
        return List.copyOf(pendingByOutbox.values());
    }
}
