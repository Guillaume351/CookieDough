package com.cookiebuild.cookiedough.lobby;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stateful but clock-driven cadence kept separate from Bukkit for deterministic tests. */
final class SkyblockGuidanceCadence {
    static final long INITIAL_DELAY_MILLIS = 10_000L;
    static final long DISPLAY_DURATION_MILLIS = 15_000L;
    static final long COOLDOWN_MILLIS = 120_000L;

    enum Decision {
        NONE,
        START,
        ACTIVE
    }

    private record State(long eligibleSince, long activeUntil, long cooldownUntil) { }

    private final long initialDelayMillis;
    private final long displayDurationMillis;
    private final long cooldownMillis;
    private final Map<UUID, State> states = new HashMap<>();

    SkyblockGuidanceCadence() {
        this(INITIAL_DELAY_MILLIS, DISPLAY_DURATION_MILLIS, COOLDOWN_MILLIS);
    }

    SkyblockGuidanceCadence(long initialDelayMillis, long displayDurationMillis, long cooldownMillis) {
        if (initialDelayMillis < 0 || displayDurationMillis <= 0 || cooldownMillis < 0) {
            throw new IllegalArgumentException("Invalid Skyblock guidance cadence");
        }
        this.initialDelayMillis = initialDelayMillis;
        this.displayDurationMillis = displayDurationMillis;
        this.cooldownMillis = cooldownMillis;
    }

    Decision evaluate(UUID playerId, boolean eligible, long nowMillis) {
        if (playerId == null) return Decision.NONE;
        State state = states.get(playerId);
        if (!eligible) {
            if (state == null || state.cooldownUntil() <= nowMillis) {
                states.remove(playerId);
            } else {
                states.put(playerId, new State(-1L, 0L, state.cooldownUntil()));
            }
            return Decision.NONE;
        }
        if (state == null) {
            states.put(playerId, new State(nowMillis, 0L, 0L));
            return Decision.NONE;
        }
        if (state.activeUntil() > nowMillis) {
            return Decision.ACTIVE;
        }
        if (state.eligibleSince() < 0L) {
            state = new State(nowMillis, 0L, state.cooldownUntil());
            states.put(playerId, state);
            return Decision.NONE;
        }
        if (nowMillis - state.eligibleSince() < initialDelayMillis || nowMillis < state.cooldownUntil()) {
            return Decision.NONE;
        }
        long activeUntil = nowMillis + displayDurationMillis;
        states.put(playerId, new State(state.eligibleSince(), activeUntil,
                activeUntil + cooldownMillis));
        return Decision.START;
    }

    void retainPlayers(Set<UUID> onlinePlayers) {
        states.keySet().retainAll(onlinePlayers);
    }

    void clear() {
        states.clear();
    }
}
