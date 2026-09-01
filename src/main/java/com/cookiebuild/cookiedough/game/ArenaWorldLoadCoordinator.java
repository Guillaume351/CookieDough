package com.cookiebuild.cookiedough.game;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Serializes the expensive Paper portion of arena preparation across every
 * minigame. Filesystem preparation may still run concurrently, but only one
 * world creation, chunk preload and validation sequence may be in flight.
 */
final class ArenaWorldLoadCoordinator {
    private static final ArenaWorldLoadCoordinator GLOBAL = new ArenaWorldLoadCoordinator();

    private final ArrayDeque<Lease<?>> waiting = new ArrayDeque<>();
    private Lease<?> active;

    static <T> ArenaPreparationPipeline.WorldLoad<T> submitGlobal(
            ArenaPreparationPipeline.Scheduler scheduler,
            Supplier<ArenaPreparationPipeline.WorldLoad<T>> loader) {
        return GLOBAL.submit(scheduler, loader);
    }

    <T> ArenaPreparationPipeline.WorldLoad<T> submit(
            ArenaPreparationPipeline.Scheduler scheduler,
            Supplier<ArenaPreparationPipeline.WorldLoad<T>> loader) {
        Lease<T> lease = new Lease<>(this, scheduler, loader);
        boolean startNow;
        synchronized (this) {
            waiting.addLast(lease);
            startNow = active == null;
            if (startNow) active = waiting.removeFirst();
        }
        if (startNow) start(lease);
        return ArenaPreparationPipeline.WorldLoad.cancellable(lease.result, lease::cancel);
    }

    private <T> void start(Lease<T> lease) {
        synchronized (this) {
            if (active != lease || lease.done) return;
            lease.started = true;
        }

        ArenaPreparationPipeline.WorldLoad<T> underlying;
        try {
            underlying = Objects.requireNonNull(lease.loader.get(), "coordinated world loader result");
        } catch (Throwable error) {
            lease.result.completeExceptionally(error);
            finish(lease);
            return;
        }

        synchronized (this) {
            if (active != lease || lease.done) {
                try {
                    underlying.cancel();
                } catch (RuntimeException ignored) {
                    // The exposed result already carries the authoritative cancellation outcome.
                }
                return;
            }
            lease.underlying = underlying;
        }

        try {
            underlying.completion().whenComplete((arena, error) -> {
                if (error == null) lease.result.complete(arena);
                else lease.result.completeExceptionally(error);
                finish(lease);
            });
        } catch (Throwable registrationError) {
            lease.result.completeExceptionally(registrationError);
            finish(lease);
        }
    }

    private boolean cancel(Lease<?> lease) {
        ArenaPreparationPipeline.WorldLoad<?> underlying;
        boolean queued;
        synchronized (this) {
            if (lease.done) return lease.result.isDone();
            queued = active != lease;
            if (queued) {
                waiting.remove(lease);
                lease.done = true;
                underlying = null;
            } else if (!lease.started) {
                lease.done = true;
                active = null;
                underlying = null;
            } else {
                underlying = lease.underlying;
                if (underlying == null) return false;
            }
        }

        if (queued || underlying == null) {
            lease.result.completeExceptionally(new CancellationException("arena world load cancelled"));
            if (!queued) scheduleNext();
            return true;
        }

        boolean cancelled = underlying.cancel();
        return cancelled && lease.result.isDone();
    }

    private void finish(Lease<?> lease) {
        synchronized (this) {
            if (lease.done) return;
            lease.done = true;
            if (active == lease) active = null;
        }
        scheduleNext();
    }

    private void scheduleNext() {
        Lease<?> next;
        synchronized (this) {
            if (active != null) return;
            next = waiting.pollFirst();
            if (next == null) return;
            active = next;
        }
        schedule(next);
    }

    private <T> void schedule(Lease<T> lease) {
        try {
            lease.scheduler.sync(() -> start(lease));
        } catch (RuntimeException rejected) {
            lease.result.completeExceptionally(rejected);
            finish(lease);
        }
    }

    private static final class Lease<T> {
        private final ArenaWorldLoadCoordinator owner;
        private final ArenaPreparationPipeline.Scheduler scheduler;
        private final Supplier<ArenaPreparationPipeline.WorldLoad<T>> loader;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private ArenaPreparationPipeline.WorldLoad<T> underlying;
        private boolean started;
        private boolean done;

        private Lease(
                ArenaWorldLoadCoordinator owner,
                ArenaPreparationPipeline.Scheduler scheduler,
                Supplier<ArenaPreparationPipeline.WorldLoad<T>> loader) {
            this.owner = owner;
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            this.loader = Objects.requireNonNull(loader, "loader");
        }

        private boolean cancel() {
            return owner.cancel(this);
        }
    }
}
