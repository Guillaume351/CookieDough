package com.cookiebuild.cookiedough.game;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Requires a sustained empty server before synchronous arena preparation. */
public final class StandbyRefillGate {
    private final long quietPeriodNanos;
    private final LongSupplier nanoTime;
    private long emptySinceNanos = Long.MIN_VALUE;

    public StandbyRefillGate(Duration quietPeriod) {
        this(quietPeriod, System::nanoTime);
    }

    StandbyRefillGate(Duration quietPeriod, LongSupplier nanoTime) {
        Objects.requireNonNull(quietPeriod, "quietPeriod");
        this.quietPeriodNanos = quietPeriod.toNanos();
        if (quietPeriodNanos < 0L) {
            throw new IllegalArgumentException("quietPeriod must not be negative");
        }
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public synchronized boolean canRefill(boolean hasOnlinePlayers, boolean hasActiveGameplay) {
        if (hasOnlinePlayers || hasActiveGameplay) {
            emptySinceNanos = Long.MIN_VALUE;
            return false;
        }
        long now = nanoTime.getAsLong();
        if (emptySinceNanos == Long.MIN_VALUE) {
            emptySinceNanos = now;
            return quietPeriodNanos == 0L;
        }
        return now - emptySinceNanos >= quietPeriodNanos;
    }
}
