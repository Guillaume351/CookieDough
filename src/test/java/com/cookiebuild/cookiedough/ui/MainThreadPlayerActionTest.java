package com.cookiebuild.cookiedough.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MainThreadPlayerActionTest {
    @Test
    void queuesActionAndRunsItOnlyWhenPlayerIsStillEligible() {
        List<Runnable> mainThreadQueue = new ArrayList<>();
        AtomicBoolean eligible = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();

        MainThreadPlayerAction.dispatch(mainThreadQueue::add, eligible::get, calls::incrementAndGet);

        assertEquals(0, calls.get(), "Cumulus callback must not mutate gameplay inline");
        assertEquals(1, mainThreadQueue.size());
        mainThreadQueue.removeFirst().run();
        assertEquals(1, calls.get());
    }

    @Test
    void dropsQueuedActionWhenPlayerWentOfflineBeforeMainThreadExecution() {
        List<Runnable> mainThreadQueue = new ArrayList<>();
        AtomicBoolean eligible = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();

        MainThreadPlayerAction.dispatch(mainThreadQueue::add, eligible::get, calls::incrementAndGet);
        eligible.set(false);
        mainThreadQueue.removeFirst().run();

        assertEquals(0, calls.get());
    }
}
