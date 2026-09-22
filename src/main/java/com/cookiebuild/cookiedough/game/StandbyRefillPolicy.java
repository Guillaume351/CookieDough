package com.cookiebuild.cookiedough.game;

/**
 * Shared policy for world-backed minigame arena pools.
 *
 * <p>Runtime refills are deliberately limited to one arena. Paper requires
 * world creation to run on the server thread, so loading several worlds in a
 * single scheduler callback would create an avoidable long tick.</p>
 */
public final class StandbyRefillPolicy {
    public static final int TARGET_SIZE = 1;
    public static final long RUNTIME_DELAY_TICKS = 1L;
    public static final long FAILURE_RETRY_TICKS = 20L * 5L;
    public static final long LOAD_BUDGET_MILLIS = 250L;

    private StandbyRefillPolicy() {
    }

    public static int runtimeBatchSize(int standbyCount, int targetSize) {
        if (standbyCount < 0 || targetSize < 1 || standbyCount > targetSize) {
            throw new IllegalArgumentException("Invalid standby pool size");
        }
        return standbyCount < targetSize ? 1 : 0;
    }

    public static long nextDelayTicks(boolean previousAttemptSucceeded) {
        return previousAttemptSucceeded ? RUNTIME_DELAY_TICKS : FAILURE_RETRY_TICKS;
    }

    public static boolean exceedsLoadBudget(long elapsedMillis) {
        if (elapsedMillis < 0L) {
            throw new IllegalArgumentException("elapsedMillis must not be negative");
        }
        return elapsedMillis > LOAD_BUDGET_MILLIS;
    }
}
