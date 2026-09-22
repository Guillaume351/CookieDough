package com.cookiebuild.cookiedough.lobby;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Schedules lifecycle-bound callbacks without letting completed async work enqueue
 * new Bukkit tasks after the owning plugin has begun shutting down.
 */
final class PluginTaskDispatcher {
    @FunctionalInterface
    interface TaskSubmitter {
        void submit(Runnable task);
    }

    private final BooleanSupplier pluginEnabled;
    private final TaskSubmitter syncSubmitter;
    private final TaskSubmitter asyncSubmitter;
    private final AtomicBoolean active = new AtomicBoolean(true);

    PluginTaskDispatcher(JavaPlugin plugin) {
        this(plugin::isEnabled,
                task -> Bukkit.getScheduler().runTask(plugin, task),
                task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    PluginTaskDispatcher(BooleanSupplier pluginEnabled, TaskSubmitter syncSubmitter,
            TaskSubmitter asyncSubmitter) {
        this.pluginEnabled = Objects.requireNonNull(pluginEnabled);
        this.syncSubmitter = Objects.requireNonNull(syncSubmitter);
        this.asyncSubmitter = Objects.requireNonNull(asyncSubmitter);
    }

    boolean runSync(Runnable task) {
        return submit(syncSubmitter, task);
    }

    boolean runAsync(Runnable task) {
        return submit(asyncSubmitter, task);
    }

    boolean isActive() {
        return active.get();
    }

    void close() {
        active.set(false);
    }

    private boolean submit(TaskSubmitter submitter, Runnable task) {
        Objects.requireNonNull(task);
        if (!active.get() || !pluginEnabled.getAsBoolean()) {
            return false;
        }

        try {
            submitter.submit(() -> {
                if (active.get() && pluginEnabled.getAsBoolean()) {
                    task.run();
                }
            });
            return true;
        } catch (IllegalPluginAccessException ignored) {
            // Paper can disable the plugin between the lifecycle check and task
            // registration. Reject that expected shutdown race quietly.
            return false;
        }
    }
}
