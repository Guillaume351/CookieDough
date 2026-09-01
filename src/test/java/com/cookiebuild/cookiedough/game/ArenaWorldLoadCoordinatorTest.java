package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ArenaWorldLoadCoordinatorTest {
    @Test
    void keepsOnlyOneCompleteWorldPreparationInFlight() {
        ArenaWorldLoadCoordinator coordinator = new ArenaWorldLoadCoordinator();
        FakeScheduler firstScheduler = new FakeScheduler();
        FakeScheduler secondScheduler = new FakeScheduler();
        CompletableFuture<String> firstResult = new CompletableFuture<>();
        AtomicInteger firstStarts = new AtomicInteger();
        AtomicInteger secondStarts = new AtomicInteger();

        ArenaPreparationPipeline.WorldLoad<String> first = coordinator.submit(firstScheduler, () -> {
            firstStarts.incrementAndGet();
            return ArenaPreparationPipeline.WorldLoad.nonCancellable(firstResult);
        });
        ArenaPreparationPipeline.WorldLoad<String> second = coordinator.submit(secondScheduler, () -> {
            secondStarts.incrementAndGet();
            return ArenaPreparationPipeline.WorldLoad.completed("second");
        });

        assertEquals(1, firstStarts.get());
        assertEquals(0, secondStarts.get(), "chunk preload and validation must finish before the next load");
        assertFalse(second.completion().toCompletableFuture().isDone());

        firstResult.complete("first");
        assertEquals("first", first.completion().toCompletableFuture().join());
        assertEquals(1, secondScheduler.sync.size());
        assertEquals(0, secondStarts.get());

        secondScheduler.runSync();
        assertEquals(1, secondStarts.get());
        assertEquals("second", second.completion().toCompletableFuture().join());
    }

    @Test
    void cancellingAQueuedLoadRemovesItWithoutBlockingTheFollowingLoad() {
        ArenaWorldLoadCoordinator coordinator = new ArenaWorldLoadCoordinator();
        FakeScheduler scheduler = new FakeScheduler();
        CompletableFuture<String> firstResult = new CompletableFuture<>();
        AtomicInteger cancelledStarts = new AtomicInteger();
        AtomicInteger finalStarts = new AtomicInteger();

        coordinator.submit(scheduler,
                () -> ArenaPreparationPipeline.WorldLoad.nonCancellable(firstResult));
        ArenaPreparationPipeline.WorldLoad<String> cancelled = coordinator.submit(scheduler, () -> {
            cancelledStarts.incrementAndGet();
            return ArenaPreparationPipeline.WorldLoad.completed("cancelled");
        });
        ArenaPreparationPipeline.WorldLoad<String> last = coordinator.submit(scheduler, () -> {
            finalStarts.incrementAndGet();
            return ArenaPreparationPipeline.WorldLoad.completed("last");
        });

        assertTrue(cancelled.cancel());
        firstResult.complete("first");
        scheduler.runSync();

        assertEquals(0, cancelledStarts.get());
        assertEquals(1, finalStarts.get());
        assertEquals("last", last.completion().toCompletableFuture().join());
    }

    @Test
    void aRejectedPluginSchedulerDoesNotWedgeOtherModes() {
        ArenaWorldLoadCoordinator coordinator = new ArenaWorldLoadCoordinator();
        FakeScheduler firstScheduler = new FakeScheduler();
        FakeScheduler rejectedScheduler = new FakeScheduler();
        FakeScheduler finalScheduler = new FakeScheduler();
        CompletableFuture<String> firstResult = new CompletableFuture<>();
        AtomicInteger finalStarts = new AtomicInteger();

        coordinator.submit(firstScheduler,
                () -> ArenaPreparationPipeline.WorldLoad.nonCancellable(firstResult));
        rejectedScheduler.rejectSync = true;
        ArenaPreparationPipeline.WorldLoad<String> rejected = coordinator.submit(rejectedScheduler,
                () -> ArenaPreparationPipeline.WorldLoad.completed("never"));
        ArenaPreparationPipeline.WorldLoad<String> last = coordinator.submit(finalScheduler, () -> {
            finalStarts.incrementAndGet();
            return ArenaPreparationPipeline.WorldLoad.completed("last");
        });

        firstResult.complete("first");
        assertTrue(rejected.completion().toCompletableFuture().isCompletedExceptionally());
        assertEquals(1, finalScheduler.sync.size());

        finalScheduler.runSync();
        assertEquals(1, finalStarts.get());
        assertEquals("last", last.completion().toCompletableFuture().join());
    }

    private static final class FakeScheduler implements ArenaPreparationPipeline.Scheduler {
        private final ArrayDeque<Runnable> sync = new ArrayDeque<>();
        private boolean rejectSync;

        @Override public void later(long delayTicks, Runnable action) { throw new UnsupportedOperationException(); }
        @Override public void async(Runnable action) { throw new UnsupportedOperationException(); }
        @Override public void sync(Runnable action) {
            if (rejectSync) throw new IllegalStateException("plugin disabled");
            sync.add(action);
        }

        private void runSync() {
            sync.removeFirst().run();
        }
    }
}
