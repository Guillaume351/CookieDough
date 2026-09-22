package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.player.CookiePlayer;

class StandbyArenaServiceTest {
    @Test
    void permanentPreparationFailureCannotBeBypassedByOneTickRequests() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger attempts = new AtomicInteger();
        StandbyArenaService<String, TestGame> service = service(
                scheduler,
                () -> "plan",
                plan -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("fixture failure");
                },
                prepared -> new TestGame(),
                () -> false,
                ignored -> { },
                ignored -> { });

        assertTrue(service.request(0L));
        scheduler.runCurrentTick();
        assertEquals(1, attempts.get());
        for (int tick = 1; tick < StandbyRefillPolicy.FAILURE_RETRY_TICKS; tick++) {
            assertFalse(service.request(StandbyRefillPolicy.RUNTIME_DELAY_TICKS));
            scheduler.advanceTo(tick);
        }
        assertEquals(1, attempts.get());
        scheduler.advanceTo(StandbyRefillPolicy.FAILURE_RETRY_TICKS);
        assertEquals(2, attempts.get());
    }

    @Test
    void failedInitialArenaOpensOnTheSecondAttemptWithoutRestart() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger opened = new AtomicInteger();
        StandbyArenaService<String, TestGame> service = service(
                scheduler,
                () -> "plan",
                plan -> {
                    if (attempts.incrementAndGet() == 1) throw new IllegalStateException("first failure");
                    return plan;
                },
                prepared -> new TestGame(),
                () -> false,
                ignored -> opened.incrementAndGet(),
                ignored -> { });

        assertTrue(service.request(0L));
        scheduler.runCurrentTick();
        assertEquals(0, opened.get());
        scheduler.advanceTo(StandbyRefillPolicy.FAILURE_RETRY_TICKS);
        assertEquals(2, attempts.get());
        assertEquals(1, opened.get());
    }

    @Test
    void plannerFailureAlsoHonoursTheHundredTickBackoff() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger plans = new AtomicInteger();
        StandbyArenaService<String, TestGame> service = service(
                scheduler,
                () -> {
                    if (plans.incrementAndGet() == 1) throw new IllegalStateException("plan failure");
                    return "plan";
                },
                plan -> plan,
                prepared -> new TestGame(),
                () -> false,
                ignored -> { },
                ignored -> { });

        assertFalse(service.request(0L));
        for (int tick = 1; tick < StandbyRefillPolicy.FAILURE_RETRY_TICKS; tick++) {
            assertFalse(service.request(StandbyRefillPolicy.RUNTIME_DELAY_TICKS));
            scheduler.advanceTo(tick);
        }
        assertEquals(1, plans.get());
        scheduler.advanceTo(StandbyRefillPolicy.FAILURE_RETRY_TICKS);
        assertEquals(2, plans.get());
    }

    @Test
    void openArenaFailureDisposesTheLoadedGameExactlyOnce() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger disposed = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();
        @SuppressWarnings("unchecked") StandbyArenaService<String, TestGame>[] holder = new StandbyArenaService[1];
        StandbyArenaService<String, TestGame> service = service(
                scheduler,
                () -> "plan",
                plan -> plan,
                prepared -> { attempts.incrementAndGet(); return new TestGame(); },
                () -> false,
                ignored -> { throw new IllegalStateException("registration failed"); },
                ignored -> {
                    disposed.incrementAndGet();
                    holder[0].request(StandbyRefillPolicy.RUNTIME_DELAY_TICKS);
                });
        holder[0] = service;

        assertTrue(service.request(0L));
        scheduler.runCurrentTick();
        assertEquals(1, disposed.get());
        scheduler.advanceTo(StandbyRefillPolicy.FAILURE_RETRY_TICKS - 1L);
        assertEquals(1, disposed.get());
        assertEquals(1, attempts.get(), "arena disposal must not bypass the failure backoff");
        scheduler.advanceTo(StandbyRefillPolicy.FAILURE_RETRY_TICKS);
        assertEquals(2, disposed.get());
        assertEquals(2, attempts.get());
    }

    @Test
    void hasOpenProbeFailureDisposesTheLoadedGameExactlyOnce() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger disposed = new AtomicInteger();
        StandbyArenaService<String, TestGame> service = service(
                scheduler,
                () -> "plan",
                plan -> plan,
                prepared -> new TestGame(),
                () -> { throw new IllegalStateException("probe failed"); },
                ignored -> { },
                ignored -> disposed.incrementAndGet());

        assertTrue(service.request(0L));
        scheduler.runCurrentTick();
        assertEquals(1, disposed.get());
        scheduler.advanceTo(StandbyRefillPolicy.FAILURE_RETRY_TICKS - 1L);
        assertEquals(1, disposed.get());
        scheduler.advanceTo(StandbyRefillPolicy.FAILURE_RETRY_TICKS);
        assertEquals(2, disposed.get());
    }

    @Test
    void promotionFailureDisposesThePolledArenaAndUsesFailureBackoff() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger disposed = new AtomicInteger();
        StandbyArenaService<String, TestGame> service = service(
                scheduler,
                () -> "plan",
                plan -> plan,
                prepared -> { loads.incrementAndGet(); return new TestGame(); },
                () -> true,
                ignored -> { throw new IllegalStateException("promotion failed"); },
                ignored -> disposed.incrementAndGet());

        assertTrue(service.request(0L));
        scheduler.runCurrentTick();
        assertEquals(1, service.standbyCount());
        assertFalse(service.activateNext());
        assertEquals(1, disposed.get());
        assertEquals(0, service.standbyCount());
        scheduler.advanceTo(StandbyRefillPolicy.FAILURE_RETRY_TICKS - 1L);
        assertEquals(1, loads.get());
        scheduler.advanceTo(StandbyRefillPolicy.FAILURE_RETRY_TICKS);
        assertEquals(2, loads.get());
    }

    @Test
    void schedulerRejectionDoesNotPreventALaterRecoveryRequest() {
        FakeScheduler scheduler = new FakeScheduler();
        scheduler.rejectLater = true;
        StandbyArenaService<String, TestGame> service = service(
                scheduler, () -> "plan", plan -> plan, prepared -> new TestGame(),
                () -> false, ignored -> { }, ignored -> { });

        assertFalse(service.request(0L));
        scheduler.rejectLater = false;
        assertTrue(service.request(0L));
        scheduler.runCurrentTick();
    }

    @Test
    void coldStandbyModePlansTheNextMapAtPromotionInsteadOfFreezingNPlusTwo() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger plans = new AtomicInteger();
        StandbyArenaService<String, TestGame> service = service(
                scheduler,
                () -> "selection-" + plans.incrementAndGet(),
                plan -> plan,
                prepared -> new TestGame(),
                () -> true,
                ignored -> { },
                ignored -> { });

        assertTrue(service.request(0L));
        scheduler.runCurrentTick();
        assertEquals(1, plans.get());
        assertEquals(1, service.standbyCount());

        assertTrue(service.activateNext());
        assertEquals(1, plans.get(), "promotion consumes the prepared N+1 without selecting N+2");
        assertEquals(0, service.standbyCount());

        assertFalse(service.activateNext());
        assertEquals(2, plans.get(), "the next selection is made only when N+1 preparation is requested");
    }

    @Test
    void shutdownCancelsScheduledPreparationAndDisposesReadyStandbys() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger disposed = new AtomicInteger();
        StandbyArenaService<String, TestGame> service = service(
                scheduler, () -> "plan", plan -> plan,
                prepared -> { loads.incrementAndGet(); return new TestGame(); },
                () -> true, ignored -> { }, ignored -> disposed.incrementAndGet());

        assertTrue(service.request(5L));
        service.shutdown();
        scheduler.advanceTo(5L);
        assertEquals(0, loads.get());

        StandbyArenaService<String, TestGame> ready = service(
                scheduler, () -> "plan", plan -> plan, prepared -> new TestGame(),
                () -> true, ignored -> { }, ignored -> disposed.incrementAndGet());
        assertTrue(ready.request(0L));
        scheduler.runCurrentTick();
        assertEquals(1, ready.standbyCount());
        ready.shutdown();
        assertEquals(1, disposed.get());
    }

    @Test
    void warmPoolStaysAvailableAcrossTwelveRotationsWithAnIdleLobbyPlayer() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger plans = new AtomicInteger();
        AtomicInteger opened = new AtomicInteger();
        int onlineLobbyPlayers = 1;
        StandbyArenaService<String, TestGame> service = new StandbyArenaService<>(
                "Fixture", scheduler, () -> "plan-" + plans.incrementAndGet(), plan -> plan,
                prepared -> new TestGame(), ignored -> { }, () -> true,
                ignored -> opened.incrementAndGet(), ignored -> { },
                Logger.getLogger("StandbyArenaServiceTest"), true);

        assertTrue(onlineLobbyPlayers > 0);
        assertTrue(service.request(0L));
        scheduler.runCurrentTick();
        assertEquals(1, service.standbyCount());
        for (int rotation = 1; rotation <= 12; rotation++) {
            assertTrue(service.activateNext(), "rotation " + rotation + " needs a prepared arena");
            scheduler.advanceTo(rotation);
            assertEquals(1, service.standbyCount());
        }
        assertEquals(12, opened.get());
        assertEquals(13, plans.get());
    }

    private static StandbyArenaService<String, TestGame> service(
            FakeScheduler scheduler,
            java.util.function.Supplier<String> planner,
            java.util.function.Function<String, String> ioPreparation,
            java.util.function.Function<String, TestGame> worldLoader,
            java.util.function.Supplier<Boolean> hasOpen,
            java.util.function.Consumer<TestGame> open,
            java.util.function.Consumer<TestGame> dispose) {
        return new StandbyArenaService<>(
                "Fixture", scheduler, planner, ioPreparation, worldLoader, ignored -> { },
                hasOpen, open, dispose, Logger.getLogger("StandbyArenaServiceTest"), false);
    }

    private static final class TestGame extends Game {
        private TestGame() { super("Fixture"); }
        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(CookiePlayer player) { }
        @Override public boolean isGameEnded() { return false; }
    }

    private static final class FakeScheduler implements ArenaPreparationPipeline.Scheduler {
        private record Scheduled(long tick, int order, Runnable action) { }
        private final PriorityQueue<Scheduled> later = new PriorityQueue<>(
                Comparator.comparingLong(Scheduled::tick).thenComparingInt(Scheduled::order));
        private final ArrayDeque<Runnable> immediate = new ArrayDeque<>();
        private long tick;
        private int order;
        private boolean rejectLater;

        @Override public void later(long delayTicks, Runnable action) {
            if (rejectLater) throw new IllegalStateException("scheduler stopped");
            later.add(new Scheduled(tick + delayTicks, order++, action));
        }
        @Override public void async(Runnable action) { immediate.add(action); }
        @Override public void sync(Runnable action) { immediate.add(action); }

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
}
