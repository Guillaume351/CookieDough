package com.cookiebuild.cookiedough.commands;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure concurrency/cooldown gate for a purpose-specific link command. */
final class LinkChallengeThrottle {
    enum Decision {
        ALLOWED,
        IN_FLIGHT,
        COOLDOWN
    }

    private final long cooldownMillis;
    private final Set<UUID> inFlight = new HashSet<>();
    private final Map<UUID, Long> lastChallengeAt = new HashMap<>();

    LinkChallengeThrottle(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }

    synchronized Decision begin(UUID playerId, long now) {
        if (inFlight.contains(playerId)) return Decision.IN_FLIGHT;
        long last = lastChallengeAt.getOrDefault(playerId, Long.MIN_VALUE / 2);
        if (now - last < cooldownMillis) return Decision.COOLDOWN;
        inFlight.add(playerId);
        lastChallengeAt.put(playerId, now);
        return Decision.ALLOWED;
    }

    synchronized void complete(UUID playerId) {
        inFlight.remove(playerId);
    }
}
