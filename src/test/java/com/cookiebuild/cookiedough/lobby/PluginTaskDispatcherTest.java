package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.plugin.IllegalPluginAccessException;
import org.junit.jupiter.api.Test;

class PluginTaskDispatcherTest {
    @Test
    void closeRejectsCallbacksBeforeTheyReachTheScheduler() {
        AtomicBoolean submitted = new AtomicBoolean();
        PluginTaskDispatcher dispatcher = new PluginTaskDispatcher(
                () -> true,
                task -> submitted.set(true),
                task -> submitted.set(true));

        dispatcher.close();

        assertFalse(dispatcher.runSync(() -> {
        }));
        assertFalse(submitted.get());
    }

    @Test
    void closeInvalidatesAnAlreadyQueuedCallback() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicBoolean callbackRan = new AtomicBoolean();
        PluginTaskDispatcher dispatcher = new PluginTaskDispatcher(
                () -> true,
                queued::set,
                queued::set);

        assertTrue(dispatcher.runSync(() -> callbackRan.set(true)));
        dispatcher.close();
        queued.get().run();

        assertFalse(callbackRan.get());
    }

    @Test
    void disabledPluginRejectsNewAsyncWork() {
        AtomicBoolean submitted = new AtomicBoolean();
        PluginTaskDispatcher dispatcher = new PluginTaskDispatcher(
                () -> false,
                task -> submitted.set(true),
                task -> submitted.set(true));

        assertFalse(dispatcher.runAsync(() -> {
        }));
        assertFalse(submitted.get());
    }

    @Test
    void taskRegistrationRaceIsRejectedDuringDisable() {
        PluginTaskDispatcher dispatcher = new PluginTaskDispatcher(
                () -> true,
                task -> {
                    throw new IllegalPluginAccessException("Plugin attempted to register task while disabled");
                },
                task -> {
                    throw new IllegalPluginAccessException("Plugin attempted to register task while disabled");
                });

        assertFalse(dispatcher.runSync(() -> {
        }));
        assertFalse(dispatcher.runAsync(() -> {
        }));
    }
}
