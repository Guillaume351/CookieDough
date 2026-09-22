package com.cookiebuild.cookiedough.game;

import java.util.Objects;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Serial arena preparation: filesystem off-thread, Paper creation on-thread, chunks asynchronously. */
public final class ArenaPreparationPipeline<P, T> {
    private static final Logger LOGGER = Logger.getLogger(ArenaPreparationPipeline.class.getName());

    public enum FailureStage { PREPARE_IO, WORLD_LOAD }

    public interface Scheduler {
        void later(long delayTicks, Runnable action);
        void async(Runnable action);
        void sync(Runnable action);
        default void cancelPending() { }
    }

    public interface Listener<T> {
        void ready(T arena, long prepareIoMillis, long worldLoadMillis);
        void failed(FailureStage stage, Throwable error, long prepareIoMillis, long worldLoadMillis);
    }

    /** Starts a Paper world load and completes on the primary server thread. */
    @FunctionalInterface
    public interface AsyncWorldLoader<P, T> {
        WorldLoad<T> apply(P prepared);
    }

    /**
     * A world load plus an authoritative main-thread cancellation hook. A hook
     * may return {@code true} only after it has detached every module callback,
     * disposed any partial Bukkit world, and completed {@link #completion()}.
     */
    public static final class WorldLoad<T> {
        private final CompletionStage<T> completion;
        private final BooleanSupplier cancellation;
        private final AtomicBoolean cancellationRequested = new AtomicBoolean();
        private volatile boolean cancellationFailed;

        private WorldLoad(CompletionStage<T> completion, BooleanSupplier cancellation) {
            this.completion = Objects.requireNonNull(completion, "completion");
            this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        }

        public static <T> WorldLoad<T> completed(T value) {
            return nonCancellable(CompletableFuture.completedFuture(value));
        }

        public static <T> WorldLoad<T> nonCancellable(CompletionStage<T> completion) {
            return new WorldLoad<>(completion, () -> false);
        }

        public static <T> WorldLoad<T> cancellable(
                CompletionStage<T> completion, BooleanSupplier cancellation) {
            return new WorldLoad<>(completion, cancellation);
        }

        public CompletionStage<T> completion() {
            return completion;
        }

        public boolean cancel() {
            if (completion.toCompletableFuture().isDone()) return true;
            if (!cancellationRequested.compareAndSet(false, true)) {
                return !cancellationFailed && completion.toCompletableFuture().isDone();
            }
            try {
                boolean succeeded = cancellation.getAsBoolean()
                        && completion.toCompletableFuture().isDone();
                cancellationFailed = !succeeded;
                return succeeded;
            } catch (RuntimeException error) {
                cancellationFailed = true;
                throw error;
            }
        }

        public <R> WorldLoad<R> map(Function<? super T, ? extends R> mapper) {
            return new WorldLoad<>(completion.thenApply(mapper), this::cancel);
        }

        private boolean cancellationFailed() {
            return cancellationFailed;
        }
    }

    /** Signals that a failed Bukkit world is still using the prepared directory. */
    public static final class PreparedFilesInUseException extends RuntimeException {
        public PreparedFilesInUseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private enum State { IDLE, SCHEDULED, PREPARING, CANCELLED }

    private final Scheduler scheduler;
    private final Supplier<P> ioPreparation;
    private final AsyncWorldLoader<P, T> worldLoader;
    private final Consumer<P> ioCleanup;
    private final Consumer<T> arenaCleanup;
    private final Listener<T> listener;
    private final LongSupplier nanoTime;
    private State state = State.IDLE;
    private int activeAsyncOperations;
    private P preparedAwaitingWorld;
    private WorldLoad<T> worldLoadInFlight;

    public ArenaPreparationPipeline(
            Scheduler scheduler,
            Supplier<P> ioPreparation,
            Function<P, T> worldLoader,
            Consumer<P> ioCleanup,
            Consumer<T> arenaCleanup,
            Listener<T> listener) {
        this(scheduler, ioPreparation,
                (AsyncWorldLoader<P, T>) prepared -> WorldLoad.completed(worldLoader.apply(prepared)),
                ioCleanup, arenaCleanup, listener, System::nanoTime);
    }

    public static <P, T> ArenaPreparationPipeline<P, T> asynchronous(
            Scheduler scheduler,
            Supplier<P> ioPreparation,
            AsyncWorldLoader<P, T> worldLoader,
            Consumer<P> ioCleanup,
            Consumer<T> arenaCleanup,
            Listener<T> listener) {
        return new ArenaPreparationPipeline<>(scheduler, ioPreparation, worldLoader,
                ioCleanup, arenaCleanup, listener, System::nanoTime);
    }

    ArenaPreparationPipeline(
            Scheduler scheduler,
            Supplier<P> ioPreparation,
            Function<P, T> worldLoader,
            Consumer<P> ioCleanup,
            Consumer<T> arenaCleanup,
            Listener<T> listener,
            LongSupplier nanoTime) {
        this(scheduler, ioPreparation,
                (AsyncWorldLoader<P, T>) prepared -> WorldLoad.completed(worldLoader.apply(prepared)),
                ioCleanup, arenaCleanup, listener, nanoTime);
    }

    static <P, T> ArenaPreparationPipeline<P, T> asynchronous(
            Scheduler scheduler,
            Supplier<P> ioPreparation,
            AsyncWorldLoader<P, T> worldLoader,
            Consumer<P> ioCleanup,
            Consumer<T> arenaCleanup,
            Listener<T> listener,
            LongSupplier nanoTime) {
        return new ArenaPreparationPipeline<>(scheduler, ioPreparation, worldLoader,
                ioCleanup, arenaCleanup, listener, nanoTime);
    }

    private ArenaPreparationPipeline(
            Scheduler scheduler,
            Supplier<P> ioPreparation,
            AsyncWorldLoader<P, T> worldLoader,
            Consumer<P> ioCleanup,
            Consumer<T> arenaCleanup,
            Listener<T> listener,
            LongSupplier nanoTime) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ioPreparation = Objects.requireNonNull(ioPreparation, "ioPreparation");
        this.worldLoader = Objects.requireNonNull(worldLoader, "worldLoader");
        this.ioCleanup = Objects.requireNonNull(ioCleanup, "ioCleanup");
        this.arenaCleanup = Objects.requireNonNull(arenaCleanup, "arenaCleanup");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public synchronized boolean request(long delayTicks) {
        if (delayTicks < 0L) throw new IllegalArgumentException("delayTicks must not be negative");
        if (state != State.IDLE) return false;
        state = State.SCHEDULED;
        try {
            scheduler.later(delayTicks, this::startPreparation);
        } catch (RuntimeException error) {
            state = State.IDLE;
            throw error;
        }
        return true;
    }

    public void cancel() {
        cancelAndAwait(Duration.ZERO);
    }

    /** Cancels queued callbacks and waits for filesystem work before the plugin classloader closes. */
    public boolean cancelAndAwait(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("timeout must not be negative");
        P stranded;
        WorldLoad<T> loading;
        synchronized (this) {
            state = State.CANCELLED;
            try {
                scheduler.cancelPending();
            } catch (RuntimeException rejection) {
                LOGGER.log(Level.WARNING, "Could not cancel queued arena preparation callbacks", rejection);
            }
            stranded = preparedAwaitingWorld;
            preparedAwaitingWorld = null;
            loading = worldLoadInFlight;
        }
        if (stranded != null) cleanupPrepared(stranded);
        if (loading != null) {
            try {
                loading.cancel();
            } catch (RuntimeException cancellationError) {
                LOGGER.log(Level.WARNING, "Could not cancel an in-flight arena world load", cancellationError);
            }
        }

        long remainingNanos = timeout.toNanos();
        long deadline = System.nanoTime() + remainingNanos;
        synchronized (this) {
            while (activeAsyncOperations > 0 && remainingNanos > 0L) {
                try {
                    long millis = Math.max(1L, remainingNanos / 1_000_000L);
                    wait(millis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                remainingNanos = deadline - System.nanoTime();
            }
            return activeAsyncOperations == 0 && (loading == null || !loading.cancellationFailed());
        }
    }

    public synchronized boolean isPending() {
        return state == State.SCHEDULED || state == State.PREPARING;
    }

    private void startPreparation() {
        synchronized (this) {
            if (state != State.SCHEDULED) return;
            state = State.PREPARING;
        }
        try {
            scheduler.async(() -> {
                beginAsyncOperation();
                try {
                    synchronized (ArenaPreparationPipeline.this) {
                        if (state == State.CANCELLED) return;
                    }
                    long ioStartedAt = nanoTime.getAsLong();
                    P prepared;
                    try {
                        prepared = ioPreparation.get();
                    } catch (Throwable error) {
                        long ioMillis = elapsedMillis(ioStartedAt);
                        synchronized (ArenaPreparationPipeline.this) {
                            if (state == State.CANCELLED) return;
                        }
                        try {
                            scheduler.sync(() -> finishFailure(FailureStage.PREPARE_IO, error, ioMillis, 0L));
                        } catch (RuntimeException rejected) {
                            resetAfterSchedulerRejection();
                        }
                        return;
                    }
                    long ioMillis = elapsedMillis(ioStartedAt);
                    synchronized (ArenaPreparationPipeline.this) {
                        if (state == State.CANCELLED) {
                            cleanupPrepared(prepared);
                            return;
                        }
                        preparedAwaitingWorld = prepared;
                    }
                    try {
                        scheduler.sync(() -> loadWorld(prepared, ioMillis));
                    } catch (RuntimeException rejected) {
                        synchronized (ArenaPreparationPipeline.this) {
                            if (preparedAwaitingWorld == prepared) preparedAwaitingWorld = null;
                        }
                        try {
                            cleanupPrepared(prepared);
                        } finally {
                            resetAfterSchedulerRejection();
                        }
                    }
                } finally {
                    endAsyncOperation();
                }
            });
        } catch (RuntimeException error) {
            finishFailure(FailureStage.PREPARE_IO, error, 0L, 0L);
        }
    }

    private void loadWorld(P prepared, long ioMillis) {
        synchronized (this) {
            if (preparedAwaitingWorld == prepared) preparedAwaitingWorld = null;
            if (state == State.CANCELLED) {
                cleanupPreparedAsync(prepared);
                return;
            }
        }
        long worldStartedAt = nanoTime.getAsLong();
        WorldLoad<T> loading;
        try {
            loading = ArenaWorldLoadCoordinator.submitGlobal(
                    scheduler, () -> worldLoader.apply(prepared));
        } catch (Throwable error) {
            long worldMillis = elapsedMillis(worldStartedAt);
            cleanupPreparedAsync(prepared);
            finishFailure(FailureStage.WORLD_LOAD, error, ioMillis, worldMillis);
            return;
        }

        beginAsyncOperation();
        synchronized (this) {
            worldLoadInFlight = loading;
        }
        try {
            loading.completion().whenComplete((arena, error) -> finishWorldLoad(
                    prepared, arena, error, ioMillis, worldStartedAt, loading));
        } catch (Throwable callbackRegistrationError) {
            synchronized (this) {
                if (worldLoadInFlight == loading) worldLoadInFlight = null;
            }
            try {
                cleanupPreparedAsync(prepared);
                finishFailure(FailureStage.WORLD_LOAD, callbackRegistrationError,
                        ioMillis, elapsedMillis(worldStartedAt));
            } finally {
                endAsyncOperation();
            }
        }
    }

    private void finishWorldLoad(P prepared, T arena, Throwable error, long ioMillis,
            long worldStartedAt, WorldLoad<T> loading) {
        long worldMillis = elapsedMillis(worldStartedAt);
        boolean cancelled;
        synchronized (this) {
            if (worldLoadInFlight == loading) worldLoadInFlight = null;
            cancelled = state == State.CANCELLED;
            if (!cancelled) state = State.IDLE;
        }
        try {
            if (error != null) {
                Throwable failure = unwrap(error);
                if (!(failure instanceof PreparedFilesInUseException)) cleanupPreparedAsync(prepared);
                if (!cancelled) finishFailure(FailureStage.WORLD_LOAD, failure, ioMillis, worldMillis);
                return;
            }
            if (arena == null) {
                Throwable nullArena = new IllegalStateException("World loader completed without an arena");
                cleanupPreparedAsync(prepared);
                if (!cancelled) finishFailure(FailureStage.WORLD_LOAD, nullArena, ioMillis, worldMillis);
                return;
            }
            if (cancelled) {
                if (disposeLoadedArena(arena, null)) cleanupPreparedAsync(prepared);
                return;
            }

            try {
                listener.ready(arena, ioMillis, worldMillis);
            } catch (Throwable callbackError) {
                if (disposeLoadedArena(arena, callbackError)) cleanupPreparedAsync(prepared);
                finishFailure(FailureStage.WORLD_LOAD, callbackError, ioMillis, worldMillis);
            }
        } finally {
            endAsyncOperation();
        }
    }

    private static Throwable unwrap(Throwable error) {
        if ((error instanceof java.util.concurrent.CompletionException
                || error instanceof java.util.concurrent.ExecutionException)
                && error.getCause() != null) return error.getCause();
        return error;
    }

    private boolean disposeLoadedArena(T arena, Throwable primaryFailure) {
        try {
            arenaCleanup.accept(arena);
            return true;
        } catch (Throwable cleanupError) {
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(cleanupError);
            }
            LOGGER.log(Level.WARNING, "Could not dispose a loaded arena after preparation was discarded",
                    cleanupError);
            return false;
        }
    }

    private void cleanupPreparedAsync(P prepared) {
        beginAsyncOperation();
        Runnable cleanup = () -> {
            try {
                cleanupPrepared(prepared);
            } finally {
                endAsyncOperation();
            }
        };
        try {
            java.util.concurrent.CompletableFuture.runAsync(cleanup);
        } catch (RuntimeException rejected) {
            try {
                cleanupPrepared(prepared);
            } finally {
                endAsyncOperation();
            }
        }
    }

    private void cleanupPrepared(P prepared) {
        try {
            ioCleanup.accept(prepared);
        } catch (Throwable cleanupError) {
            LOGGER.log(Level.WARNING, "Could not delete discarded arena preparation files", cleanupError);
        }
    }

    private synchronized void resetAfterSchedulerRejection() {
        if (state != State.CANCELLED) state = State.IDLE;
    }

    private synchronized void beginAsyncOperation() {
        activeAsyncOperations++;
    }

    private synchronized void endAsyncOperation() {
        activeAsyncOperations--;
        notifyAll();
    }

    private void finishFailure(
            FailureStage stage, Throwable error, long ioMillis, long worldMillis) {
        synchronized (this) {
            if (state == State.CANCELLED) return;
            state = State.IDLE;
        }
        listener.failed(stage, error, ioMillis, worldMillis);
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (nanoTime.getAsLong() - startedAt) / 1_000_000L);
    }
}
