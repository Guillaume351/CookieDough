package com.cookiebuild.cookiedough.game;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Bukkit scheduler adapter used by {@link ArenaPreparationPipeline}. */
public final class BukkitArenaPreparationScheduler implements ArenaPreparationPipeline.Scheduler {
    private final JavaPlugin plugin;
    private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();
    private final Object lifecycleLock = new Object();
    private boolean stopped;

    public BukkitArenaPreparationScheduler(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void later(long delayTicks, Runnable action) {
        track(wrapped -> plugin.getServer().getScheduler().runTaskLater(plugin, wrapped, delayTicks), action);
    }

    @Override
    public void async(Runnable action) {
        track(wrapped -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, wrapped), action);
    }

    @Override
    public void sync(Runnable action) {
        track(wrapped -> plugin.getServer().getScheduler().runTask(plugin, wrapped), action);
    }

    @Override
    public void cancelPending() {
        synchronized (lifecycleLock) {
            stopped = true;
        }
        for (BukkitTask task : tasks.toArray(BukkitTask[]::new)) {
            task.cancel();
            tasks.remove(task);
        }
    }

    private void track(Function<Runnable, BukkitTask> submit, Runnable action) {
        Object registration = new Object();
        AtomicReference<BukkitTask> reference = new AtomicReference<>();
        Runnable wrapped = () -> {
            BukkitTask task;
            synchronized (registration) {
                task = reference.get();
            }
            try {
                action.run();
            } finally {
                if (task != null) tasks.remove(task);
            }
        };
        synchronized (lifecycleLock) {
            if (stopped) throw new IllegalStateException("Arena preparation scheduler is stopped");
            synchronized (registration) {
                BukkitTask task = submit.apply(wrapped);
                reference.set(task);
                tasks.add(task);
            }
        }
    }
}
