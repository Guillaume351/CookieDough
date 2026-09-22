package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ArenaPreparationPipelineTest {
    @Test
    void cancelAndAwaitKeepsCleanupInsideThePluginClassloaderLifetime() throws Exception {
        ConcurrentStageScheduler scheduler = new ConcurrentStageScheduler();
        CountDownLatch ioStarted = new CountDownLatch(1);
        CountDownLatch releaseIo = new CountDownLatch(1);
        AtomicBoolean classloaderOpen = new AtomicBoolean(true);
        AtomicInteger cleaned = new AtomicInteger();
        ArenaPreparationPipeline<String, String> pipeline = new ArenaPreparationPipeline<>(
                scheduler,
                () -> {
                    ioStarted.countDown();
                    await(releaseIo);
                    return "prepared";
                },
                value -> value,
                ignored -> {
                    assertTrue(classloaderOpen.get(), "cleanup must finish before JavaPlugin teardown returns");
                    cleaned.incrementAndGet();
                },
                ignored -> { },
                new NoopListener<>());

        assertTrue(pipeline.request(0L));
        scheduler.runLater();
        assertTrue(ioStarted.await(2, TimeUnit.SECONDS));

        CompletableFuture<Boolean> drained = CompletableFuture.supplyAsync(
                () -> pipeline.cancelAndAwait(Duration.ofSeconds(2)));
        assertTrue(scheduler.cancelled.await(2, TimeUnit.SECONDS));
        releaseIo.countDown();
        assertTrue(drained.get(2, TimeUnit.SECONDS));
        classloaderOpen.set(false);

        assertEquals(1, cleaned.get());
        assertFalse(scheduler.hasSyncCallback());
        assertFalse(pipeline.isPending());
    }

    @Test
    void cancelAndAwaitCleansPreparedFilesWaitingForTheMainThreadExactlyOnce() throws Exception {
        ConcurrentStageScheduler scheduler = new ConcurrentStageScheduler();
        AtomicInteger cleaned = new AtomicInteger();
        ArenaPreparationPipeline<String, String> pipeline = new ArenaPreparationPipeline<>(
                scheduler,
                () -> "prepared",
                value -> value,
                ignored -> cleaned.incrementAndGet(),
                ignored -> { },
                new NoopListener<>());

        assertTrue(pipeline.request(0L));
        scheduler.runLater();
        assertTrue(scheduler.syncQueued.await(2, TimeUnit.SECONDS));
        assertTrue(pipeline.cancelAndAwait(Duration.ofSeconds(2)));

        assertEquals(1, cleaned.get());
        assertFalse(scheduler.hasSyncCallback(), "the callback referencing plugin classes must be cancelled");
        assertFalse(pipeline.isPending());
    }

    @Test
    void cancelDoesNotReportFalseQuiescenceWhileUnderlyingWorldFutureCanStillCallBack() {
        FakeScheduler scheduler = new FakeScheduler();
        CompletableFuture<String> underlyingWorldLoad = new CompletableFuture<>();
        AtomicBoolean classloaderOpen = new AtomicBoolean(true);
        AtomicInteger cleaned = new AtomicInteger();
        ArenaPreparationPipeline<String, String> pipeline = ArenaPreparationPipeline.asynchronous(
                scheduler,
                () -> "prepared",
                (ArenaPreparationPipeline.AsyncWorldLoader<String, String>) ignored ->
                        ArenaPreparationPipeline.WorldLoad.nonCancellable(underlyingWorldLoad),
                ignored -> {
                    assertTrue(classloaderOpen.get(), "late cleanup must remain inside shutdown drain");
                    cleaned.incrementAndGet();
                },
                ignored -> { },
                new NoopListener<>());

        assertTrue(pipeline.request(0L));
        scheduler.runCurrentTick();
        assertTrue(pipeline.isPending());
        assertFalse(pipeline.cancelAndAwait(Duration.ZERO),
                "an uncancelled Paper callback must keep the pipeline non-quiescent");
        assertFalse(underlyingWorldLoad.isCancelled());

        underlyingWorldLoad.completeExceptionally(new IllegalStateException("server stopping"));
        assertTrue(pipeline.cancelAndAwait(Duration.ofSeconds(2)));
        classloaderOpen.set(false);
        assertEquals(1, cleaned.get());
        assertFalse(pipeline.isPending());
    }

    @Test
    void worldLoadMetricIncludesTheAsynchronousChunkPreloadWallTime() {
        FakeScheduler scheduler = new FakeScheduler();
        CompletableFuture<String> world = new CompletableFuture<>();
        AtomicInteger measuredMillis = new AtomicInteger();
        ArenaPreparationPipeline<String, String> pipeline = ArenaPreparationPipeline.asynchronous(
                scheduler,
                () -> "prepared",
                (ArenaPreparationPipeline.AsyncWorldLoader<String, String>) ignored ->
                        ArenaPreparationPipeline.WorldLoad.nonCancellable(world),
                ignored -> { },
                ignored -> { },
                new ArenaPreparationPipeline.Listener<>() {
                    @Override public void ready(String arena, long io, long worldMillis) {
                        measuredMillis.set(Math.toIntExact(worldMillis));
                    }
                    @Override public void failed(
                            ArenaPreparationPipeline.FailureStage stage, Throwable error, long io, long worldMillis) { }
                },
                scheduler::nanoTime);

        assertTrue(pipeline.request(0L));
        scheduler.runCurrentTick();
        scheduler.advanceTo(20L);
        world.complete("loaded");

        assertEquals(1_000, measuredMillis.get());
        assertFalse(pipeline.isPending());
    }

    @Test
    void authoritativeWorldCancellationDetachesCallbackAndDrainsWithoutWaiting() {
        FakeScheduler scheduler = new FakeScheduler();
        CompletableFuture<String> paperChunkFuture = new CompletableFuture<>();
        CompletableFuture<String> result = new CompletableFuture<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger moduleCallbacks = new AtomicInteger();
        AtomicInteger partialWorldDisposals = new AtomicInteger();
        AtomicInteger preparedCleanups = new AtomicInteger();
        paperChunkFuture.whenComplete((value, error) -> {
            if (cancelled.get()) return;
            moduleCallbacks.incrementAndGet();
            if (error == null) result.complete(value);
            else result.completeExceptionally(error);
        });
        ArenaPreparationPipeline<String, String> pipeline = ArenaPreparationPipeline.asynchronous(
                scheduler,
                () -> "prepared",
                (ArenaPreparationPipeline.AsyncWorldLoader<String, String>) ignored ->
                        ArenaPreparationPipeline.WorldLoad.cancellable(result, () -> {
                            cancelled.set(true);
                            paperChunkFuture.cancel(false);
                            partialWorldDisposals.incrementAndGet();
                            result.completeExceptionally(new java.util.concurrent.CancellationException(
                                    "plugin stopping"));
                            return true;
                        }),
                ignored -> preparedCleanups.incrementAndGet(),
                ignored -> { },
                new NoopListener<>());

        assertTrue(pipeline.request(0L));
        scheduler.runCurrentTick();
        assertTrue(pipeline.cancelAndAwait(Duration.ofSeconds(2)));

        assertEquals(0, moduleCallbacks.get());
        assertEquals(1, partialWorldDisposals.get());
        assertEquals(1, preparedCleanups.get());
        assertFalse(pipeline.isPending());
    }

    @Test
    void failedPartialWorldUnloadRetainsItsDirectoryAndReportsNonQuiescentShutdown() {
        FakeScheduler scheduler = new FakeScheduler();
        CompletableFuture<String> result = new CompletableFuture<>();
        AtomicInteger preparedCleanups = new AtomicInteger();
        ArenaPreparationPipeline<String, String> pipeline = ArenaPreparationPipeline.asynchronous(
                scheduler,
                () -> "prepared",
                (ArenaPreparationPipeline.AsyncWorldLoader<String, String>) ignored ->
                        ArenaPreparationPipeline.WorldLoad.cancellable(result, () -> {
                            result.completeExceptionally(
                                    new ArenaPreparationPipeline.PreparedFilesInUseException(
                                            "partial world remains loaded",
                                            new IllegalStateException("Paper refused unload")));
                            return false;
                        }),
                ignored -> preparedCleanups.incrementAndGet(),
                ignored -> { },
                new NoopListener<>());

        assertTrue(pipeline.request(0L));
        scheduler.runCurrentTick();
        assertFalse(pipeline.cancelAndAwait(Duration.ofSeconds(2)));
        assertEquals(0, preparedCleanups.get(), "never delete files still used by a loaded Bukkit world");
    }

    @Test
    void permanentFailureCannotRetryBeforeTheHundredTickBackoff() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger attempts = new AtomicInteger();
        @SuppressWarnings("unchecked") ArenaPreparationPipeline<String, String>[] pipeline = new ArenaPreparationPipeline[1];
        pipeline[0] = new ArenaPreparationPipeline<>(
                scheduler,
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("fixture failure");
                },
                prepared -> prepared,
                ignored -> { },
                ignored -> { },
                new ArenaPreparationPipeline.Listener<>() {
                    @Override public void ready(String arena, long io, long world) { }
                    @Override public void failed(
                            ArenaPreparationPipeline.FailureStage stage, Throwable error, long io, long world) {
                        pipeline[0].request(StandbyRefillPolicy.FAILURE_RETRY_TICKS);
                    }
                },
                scheduler::nanoTime);

        assertTrue(pipeline[0].request(0));
        scheduler.runCurrentTick();
        assertEquals(1, attempts.get());
        scheduler.advanceTo(99);
        assertEquals(1, attempts.get());
        scheduler.advanceTo(100);
        assertEquals(2, attempts.get());
    }

    @Test
    void failedInitialOpenRecoversOnTheSecondAttemptWithoutRestart() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger ready = new AtomicInteger();
        @SuppressWarnings("unchecked") ArenaPreparationPipeline<String, String>[] pipeline = new ArenaPreparationPipeline[1];
        pipeline[0] = new ArenaPreparationPipeline<>(
                scheduler,
                () -> {
                    if (attempts.incrementAndGet() == 1) throw new IllegalStateException("first load fails");
                    return "prepared";
                },
                prepared -> "open-" + prepared,
                ignored -> { },
                ignored -> { },
                new ArenaPreparationPipeline.Listener<>() {
                    @Override public void ready(String arena, long io, long world) {
                        assertEquals("open-prepared", arena);
                        ready.incrementAndGet();
                    }
                    @Override public void failed(
                            ArenaPreparationPipeline.FailureStage stage, Throwable error, long io, long world) {
                        pipeline[0].request(StandbyRefillPolicy.FAILURE_RETRY_TICKS);
                    }
                },
                scheduler::nanoTime);

        pipeline[0].request(0);
        scheduler.runCurrentTick();
        assertEquals(0, ready.get());
        scheduler.advanceTo(100);
        assertEquals(2, attempts.get());
        assertEquals(1, ready.get());
        assertFalse(pipeline[0].isPending());
    }

    @Test
    void cancelDuringWorldLoadDisposesTheCreatedArena() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger disposed = new AtomicInteger();
        AtomicInteger cleaned = new AtomicInteger();
        @SuppressWarnings("unchecked") ArenaPreparationPipeline<String, String>[] pipeline = new ArenaPreparationPipeline[1];
        pipeline[0] = new ArenaPreparationPipeline<>(
                scheduler,
                () -> "prepared",
                prepared -> {
                    pipeline[0].cancel();
                    return "loaded";
                },
                ignored -> cleaned.incrementAndGet(),
                ignored -> disposed.incrementAndGet(),
                new NoopListener<>(),
                scheduler::nanoTime);

        pipeline[0].request(0L);
        scheduler.runCurrentTick();
        assertTrue(pipeline[0].cancelAndAwait(Duration.ofSeconds(2)));
        assertEquals(1, disposed.get());
        assertEquals(1, cleaned.get());
        assertFalse(pipeline[0].isPending());
    }

    @Test
    void rejectedSchedulingDoesNotLeaveThePipelineWedged() {
        FakeScheduler scheduler = new FakeScheduler();
        scheduler.rejectLater = true;
        ArenaPreparationPipeline<String, String> pipeline = new ArenaPreparationPipeline<>(
                scheduler, () -> "prepared", value -> value, ignored -> { }, ignored -> { },
                new NoopListener<>(), scheduler::nanoTime);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> pipeline.request(0L));
        assertFalse(pipeline.isPending());
    }

    @Test
    void rejectedAsyncDispatchDoesNotLeaveThePipelineWedged() {
        FakeScheduler scheduler = new FakeScheduler();
        scheduler.rejectAsync = true;
        ArenaPreparationPipeline<String, String> pipeline = new ArenaPreparationPipeline<>(
                scheduler, () -> "prepared", value -> value, ignored -> { }, ignored -> { },
                new NoopListener<>(), scheduler::nanoTime);

        assertTrue(pipeline.request(0L));
        scheduler.runCurrentTick();
        assertFalse(pipeline.isPending());
    }

    @Test
    void rejectedSyncDispatchCleansPreparedFilesAndResetsThePipeline() {
        FakeScheduler scheduler = new FakeScheduler();
        scheduler.rejectSync = true;
        AtomicInteger cleaned = new AtomicInteger();
        ArenaPreparationPipeline<String, String> pipeline = new ArenaPreparationPipeline<>(
                scheduler, () -> "prepared", value -> value, ignored -> {
                    cleaned.incrementAndGet();
                    throw new IllegalStateException("cleanup fixture failed");
                },
                ignored -> { }, new NoopListener<>(), scheduler::nanoTime);

        assertTrue(pipeline.request(0L));
        scheduler.runCurrentTick();
        assertEquals(1, cleaned.get());
        assertFalse(pipeline.isPending());
    }

    @Test
    void readyCallbackFailureDisposesLoadedArenaExactlyOnce() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger disposed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        ArenaPreparationPipeline<String, String> pipeline = new ArenaPreparationPipeline<>(
                scheduler,
                () -> "prepared",
                value -> "loaded",
                ignored -> { },
                ignored -> disposed.incrementAndGet(),
                new ArenaPreparationPipeline.Listener<>() {
                    @Override public void ready(String arena, long io, long world) {
                        throw new IllegalStateException("open failed");
                    }

                    @Override public void failed(
                            ArenaPreparationPipeline.FailureStage stage, Throwable error, long io, long world) {
                        failed.incrementAndGet();
                    }
                },
                scheduler::nanoTime);

        assertTrue(pipeline.request(0L));
        scheduler.runCurrentTick();
        assertEquals(1, disposed.get());
        assertEquals(1, failed.get());
        assertFalse(pipeline.isPending());
    }

    @Test
    void arenaCleanupFailureIsAttachedToTheReadyCallbackFailure() {
        FakeScheduler scheduler = new FakeScheduler();
        java.util.concurrent.atomic.AtomicReference<Throwable> reported = new java.util.concurrent.atomic.AtomicReference<>();
        ArenaPreparationPipeline<String, String> pipeline = new ArenaPreparationPipeline<>(
                scheduler,
                () -> "prepared",
                value -> "loaded",
                ignored -> { },
                ignored -> { throw new IllegalStateException("cleanup failed"); },
                new ArenaPreparationPipeline.Listener<>() {
                    @Override public void ready(String arena, long io, long world) {
                        throw new IllegalStateException("registration failed");
                    }

                    @Override public void failed(
                            ArenaPreparationPipeline.FailureStage stage, Throwable error, long io, long world) {
                        reported.set(error);
                    }
                },
                scheduler::nanoTime);

        assertTrue(pipeline.request(0L));
        scheduler.runCurrentTick();
        assertEquals("registration failed", reported.get().getMessage());
        assertEquals(1, reported.get().getSuppressed().length);
        assertEquals("cleanup failed", reported.get().getSuppressed()[0].getMessage());
    }

    @Test
    void filesystemPreparationAndWorldLoadUseTheirDedicatedSchedulerStages() {
        StageScheduler scheduler = new StageScheduler();
        ArenaPreparationPipeline<String, String> pipeline = new ArenaPreparationPipeline<>(
                scheduler,
                () -> {
                    assertEquals(StageScheduler.Stage.ASYNC, scheduler.stage);
                    return "prepared";
                },
                prepared -> {
                    assertEquals(StageScheduler.Stage.SYNC, scheduler.stage);
                    return "loaded";
                },
                ignored -> { },
                ignored -> { },
                new NoopListener<>());

        assertTrue(pipeline.request(0L));
        scheduler.runLater();
        scheduler.runAsync();
        scheduler.runSync();
        assertFalse(pipeline.isPending());
    }

    private static final class NoopListener<T> implements ArenaPreparationPipeline.Listener<T> {
        @Override public void ready(T arena, long io, long world) { }
        @Override public void failed(
                ArenaPreparationPipeline.FailureStage stage, Throwable error, long io, long world) { }
    }

    private static final class FakeScheduler implements ArenaPreparationPipeline.Scheduler {
        private record Scheduled(long tick, int order, Runnable action) { }
        private final PriorityQueue<Scheduled> later = new PriorityQueue<>(
                Comparator.comparingLong(Scheduled::tick).thenComparingInt(Scheduled::order));
        private final ArrayDeque<Runnable> immediate = new ArrayDeque<>();
        private long tick;
        private int order;
        private boolean rejectLater;
        private boolean rejectAsync;
        private boolean rejectSync;

        @Override public void later(long delayTicks, Runnable action) {
            if (rejectLater) throw new IllegalStateException("scheduler stopped");
            later.add(new Scheduled(tick + delayTicks, order++, action));
        }
        @Override public void async(Runnable action) {
            if (rejectAsync) throw new IllegalStateException("async scheduler stopped");
            immediate.add(action);
        }
        @Override public void sync(Runnable action) {
            if (rejectSync) throw new IllegalStateException("sync scheduler stopped");
            immediate.add(action);
        }
        long nanoTime() { return tick * 50_000_000L; }

        void runCurrentTick() {
            boolean progressed;
            do {
                progressed = false;
                while (!later.isEmpty() && later.peek().tick() <= tick) {
                    immediate.add(later.remove().action());
                    progressed = true;
                }
                while (!immediate.isEmpty()) {
                    immediate.remove().run();
                    progressed = true;
                }
            } while (progressed && !later.isEmpty() && later.peek().tick() <= tick);
        }

        void advanceTo(long targetTick) {
            while (tick < targetTick) {
                tick++;
                runCurrentTick();
            }
        }
    }

    private static final class StageScheduler implements ArenaPreparationPipeline.Scheduler {
        private enum Stage { NONE, LATER, ASYNC, SYNC }
        private Stage stage = Stage.NONE;
        private Runnable later;
        private Runnable async;
        private Runnable sync;

        @Override public void later(long delayTicks, Runnable action) { later = action; }
        @Override public void async(Runnable action) { async = action; }
        @Override public void sync(Runnable action) { sync = action; }

        void runLater() { run(Stage.LATER, later); }
        void runAsync() { run(Stage.ASYNC, async); }
        void runSync() { run(Stage.SYNC, sync); }

        private void run(Stage next, Runnable action) {
            stage = next;
            action.run();
            stage = Stage.NONE;
        }
    }

    private static final class ConcurrentStageScheduler implements ArenaPreparationPipeline.Scheduler {
        private final CountDownLatch syncQueued = new CountDownLatch(1);
        private final CountDownLatch cancelled = new CountDownLatch(1);
        private volatile Runnable later;
        private volatile Runnable sync;

        @Override public void later(long delayTicks, Runnable action) { later = action; }
        @Override public void async(Runnable action) {
            Thread worker = new Thread(action, "arena-preparation-test");
            worker.setDaemon(true);
            worker.start();
        }
        @Override public void sync(Runnable action) {
            sync = action;
            syncQueued.countDown();
        }
        @Override public void cancelPending() {
            later = null;
            sync = null;
            cancelled.countDown();
        }

        void runLater() {
            Runnable action = later;
            later = null;
            action.run();
        }

        boolean hasSyncCallback() { return sync != null; }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("fixture interrupted", interrupted);
        }
    }
}
