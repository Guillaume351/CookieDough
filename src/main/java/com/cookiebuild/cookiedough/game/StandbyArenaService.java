package com.cookiebuild.cookiedough.game;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Shared one-open/one-standby lifecycle built on the cancellable preparation pipeline. */
public final class StandbyArenaService<P, T extends Game> {
    private static final Duration SHUTDOWN_DRAIN_TIMEOUT = Duration.ofSeconds(10);
    private final String mode;
    private final ArenaPreparationPipeline.Scheduler scheduler;
    private final Supplier<P> planner;
    private final StandbyGamePool<T> standbys = new StandbyGamePool<>(StandbyRefillPolicy.TARGET_SIZE);
    private final ArenaPreparationPipeline<P, T> pipeline;
    private final Supplier<Boolean> hasOpenArena;
    private final Consumer<T> openArena;
    private final Consumer<T> disposeArena;
    private final Logger logger;
    private final boolean keepWarmStandby;
    private final AtomicReference<P> planned = new AtomicReference<>();
    private boolean planRetryScheduled;
    private boolean discardingArena;
    private boolean stopped;

    public static <P, T extends Game> StandbyArenaService<P, T> asynchronous(
            String mode,
            ArenaPreparationPipeline.Scheduler scheduler,
            Supplier<P> planner,
            Function<P, P> ioPreparation,
            ArenaPreparationPipeline.AsyncWorldLoader<P, T> worldLoader,
            Consumer<P> ioCleanup,
            Supplier<Boolean> hasOpenArena,
            Consumer<T> openArena,
            Consumer<T> disposeArena,
            Logger logger,
            boolean keepWarmStandby) {
        return new StandbyArenaService<>(mode, scheduler, planner, ioPreparation, worldLoader,
                ioCleanup, hasOpenArena, openArena, disposeArena, logger, keepWarmStandby);
    }

    public StandbyArenaService(
            String mode,
            ArenaPreparationPipeline.Scheduler scheduler,
            Supplier<P> planner,
            Function<P, P> ioPreparation,
            Function<P, T> worldLoader,
            Consumer<P> ioCleanup,
            Supplier<Boolean> hasOpenArena,
            Consumer<T> openArena,
            Consumer<T> disposeArena,
            Logger logger,
            boolean keepWarmStandby) {
        this(mode, scheduler, planner, ioPreparation,
                (ArenaPreparationPipeline.AsyncWorldLoader<P, T>) prepared ->
                        ArenaPreparationPipeline.WorldLoad.completed(worldLoader.apply(prepared)),
                ioCleanup, hasOpenArena, openArena, disposeArena, logger, keepWarmStandby);
    }

    private StandbyArenaService(
            String mode,
            ArenaPreparationPipeline.Scheduler scheduler,
            Supplier<P> planner,
            Function<P, P> ioPreparation,
            ArenaPreparationPipeline.AsyncWorldLoader<P, T> worldLoader,
            Consumer<P> ioCleanup,
            Supplier<Boolean> hasOpenArena,
            Consumer<T> openArena,
            Consumer<T> disposeArena,
            Logger logger,
            boolean keepWarmStandby) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.hasOpenArena = Objects.requireNonNull(hasOpenArena, "hasOpenArena");
        this.openArena = Objects.requireNonNull(openArena, "openArena");
        this.disposeArena = Objects.requireNonNull(disposeArena, "disposeArena");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.keepWarmStandby = keepWarmStandby;
        this.pipeline = ArenaPreparationPipeline.asynchronous(
                scheduler,
                () -> ioPreparation.apply(requirePlanned()),
                worldLoader,
                ioCleanup,
                this::disposeDiscardedArena,
                new ArenaPreparationPipeline.Listener<>() {
                    @Override
                    public void ready(T arena, long prepareIoMillis, long worldLoadMillis) {
                        onReady(arena, prepareIoMillis, worldLoadMillis);
                    }

                    @Override
                    public void failed(ArenaPreparationPipeline.FailureStage stage, Throwable error,
                            long prepareIoMillis, long worldLoadMillis) {
                        onFailure(stage, error, prepareIoMillis, worldLoadMillis);
                    }
                });
    }

    public synchronized boolean request(long delayTicks) {
        if (stopped || discardingArena || planRetryScheduled || pipeline.isPending()
                || !standbys.needsRefill()) return false;
        try {
            if (!planned.compareAndSet(null, planner.get())) return false;
            if (!pipeline.request(delayTicks)) {
                planned.set(null);
                return false;
            }
            return true;
        } catch (RuntimeException error) {
            planned.set(null);
            logger.warning(mode + " arena plan failed: " + rootMessage(error));
            schedulePlanRetry();
            return false;
        }
    }

    public synchronized boolean activateNext() {
        if (stopped) return false;
        T arena = standbys.poll();
        if (arena == null) {
            logger.warning("No prepared " + mode + " standby is available; recovery is scheduled");
            request(StandbyRefillPolicy.RUNTIME_DELAY_TICKS);
            return false;
        }
        try {
            openArena.accept(arena);
        } catch (RuntimeException error) {
            logger.warning(mode + " prepared arena promotion failed: " + rootMessage(error));
            disposeDiscardedArenaQuietly(arena);
            request(StandbyRefillPolicy.FAILURE_RETRY_TICKS);
            return false;
        }
        logger.info("Activated prepared " + mode + " arena " + arena.getGameId());
        if (keepWarmStandby) request(StandbyRefillPolicy.RUNTIME_DELAY_TICKS);
        return true;
    }

    public void shutdown() {
        synchronized (this) {
            if (stopped) return;
            stopped = true;
            planned.set(null);
        }

        long startedAt = System.nanoTime();
        boolean drained = pipeline.cancelAndAwait(SHUTDOWN_DRAIN_TIMEOUT);
        long drainMillis = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        if (!drained) {
            logger.warning(mode + " arena preparation did not quiesce within "
                    + SHUTDOWN_DRAIN_TIMEOUT.toMillis() + "ms; plugin shutdown continues"
                    + " (shutdown_drain_ms=" + drainMillis + ")");
        } else if (drainMillis > StandbyRefillPolicy.LOAD_BUDGET_MILLIS) {
            logger.info(mode + " arena preparation drained before classloader shutdown"
                    + " (shutdown_drain_ms=" + drainMillis + ")");
        }

        List<T> ready = standbys.drain();
        for (T arena : ready) disposeDiscardedArenaQuietly(arena);
    }

    public int standbyCount() {
        return standbys.size();
    }

    private P requirePlanned() {
        P value = planned.getAndSet(null);
        if (value == null) throw new IllegalStateException("No arena plan is available");
        return value;
    }

    private synchronized void onReady(T arena, long prepareIoMillis, long worldLoadMillis) {
        if (stopped) {
            disposeDiscardedArenaQuietly(arena);
            return;
        }
        logTimings(arena, prepareIoMillis, worldLoadMillis);
        if (!hasOpenArena.get()) {
            openArena.accept(arena);
            if (keepWarmStandby) request(StandbyRefillPolicy.RUNTIME_DELAY_TICKS);
            return;
        }
        if (!standbys.offer(arena)) disposeDiscardedArenaQuietly(arena);
    }

    private synchronized void onFailure(ArenaPreparationPipeline.FailureStage stage, Throwable error,
            long prepareIoMillis, long worldLoadMillis) {
        logger.warning(mode + " arena preparation failed at " + stage
                + " (prepare_io_ms=" + prepareIoMillis + ", world_load_ms=" + worldLoadMillis
                + ", load_ms=" + totalMillis(prepareIoMillis, worldLoadMillis) + "): "
                + rootMessage(error));
        request(StandbyRefillPolicy.FAILURE_RETRY_TICKS);
    }

    private synchronized void schedulePlanRetry() {
        if (stopped || planRetryScheduled) return;
        planRetryScheduled = true;
        try {
            scheduler.later(StandbyRefillPolicy.FAILURE_RETRY_TICKS, () -> {
                synchronized (StandbyArenaService.this) {
                    planRetryScheduled = false;
                }
                request(0L);
            });
        } catch (RuntimeException rejected) {
            planRetryScheduled = false;
            logger.warning(mode + " arena retry could not be scheduled: " + rootMessage(rejected));
        }
    }

    private void disposeDiscardedArena(T arena) {
        synchronized (this) {
            discardingArena = true;
        }
        try {
            disposeArena.accept(arena);
        } finally {
            synchronized (this) {
                discardingArena = false;
            }
        }
    }

    private void disposeDiscardedArenaQuietly(T arena) {
        try {
            disposeDiscardedArena(arena);
        } catch (RuntimeException error) {
            logger.warning(mode + " discarded arena cleanup failed: " + rootMessage(error));
        }
    }

    private void logTimings(T arena, long prepareIoMillis, long worldLoadMillis) {
        logger.info("Prepared " + mode + " arena " + arena.getGameId()
                + " (prepare_io_ms=" + prepareIoMillis + ", world_load_ms=" + worldLoadMillis
                + ", load_ms=" + totalMillis(prepareIoMillis, worldLoadMillis) + ")");
        if (StandbyRefillPolicy.exceedsLoadBudget(worldLoadMillis)) {
            logger.warning(mode + " world readiness exceeded the " + StandbyRefillPolicy.LOAD_BUDGET_MILLIS
                    + "ms target (world_load_ms=" + worldLoadMillis
                    + "); chunk preload is asynchronous and validation has a separate main-thread metric");
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    private static long totalMillis(long prepareIoMillis, long worldLoadMillis) {
        if (Long.MAX_VALUE - prepareIoMillis < worldLoadMillis) return Long.MAX_VALUE;
        return prepareIoMillis + worldLoadMillis;
    }
}
