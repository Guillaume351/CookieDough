package com.cookiebuild.cookiedough.game;

/**
 * Pure solo-start rule behind {@link Game#soloStartAfterSeconds()}. A mode
 * opts in by returning a non-negative delay; the configured minimum is
 * otherwise never relaxed.
 */
public final class QueueStartPolicy {
    public static final int SOLO_START_DISABLED = -1;

    private QueueStartPolicy() { }

    /**
     * Returns the headcount required to count down. It becomes one only when
     * exactly one player has waited at least the mode's solo-start delay.
     */
    public static int effectiveMinimumPlayers(int minimumPlayers, int playerCount,
            long longestWaitSeconds, int soloStartAfterSeconds) {
        if (soloStartAfterSeconds < 0 || playerCount != 1 || longestWaitSeconds < soloStartAfterSeconds) {
            return minimumPlayers;
        }
        return Math.min(1, minimumPlayers);
    }

    /** Seconds before a lone player may start alone, or -1 when solo start does not apply. */
    public static long secondsUntilSoloStart(int playerCount, long longestWaitSeconds, int soloStartAfterSeconds) {
        if (soloStartAfterSeconds < 0 || playerCount != 1) return -1L;
        return Math.max(0L, soloStartAfterSeconds - Math.max(0L, longestWaitSeconds));
    }

    /**
     * Seconds before a lone player's match really starts: the remaining solo
     * delay plus the countdown that then runs. The countdown's first second
     * elapses in the same game tick that ends the solo delay, so it adds
     * {@code countdownSeconds - 1}: the value shown one second before the
     * countdown equals the countdown's first value plus one, never a jump.
     * Returns -1 when solo start does not apply. Player-facing texts use this
     * so "starts in N s" is never followed by a second, unannounced countdown.
     */
    public static int secondsUntilSoloMatch(int playerCount, long longestWaitSeconds, int soloStartAfterSeconds,
            int countdownSeconds) {
        long remaining = secondsUntilSoloStart(playerCount, longestWaitSeconds, soloStartAfterSeconds);
        if (remaining < 0L) return -1;
        return (int) Math.min(Integer.MAX_VALUE, remaining + Math.max(0, countdownSeconds - 1));
    }
}
