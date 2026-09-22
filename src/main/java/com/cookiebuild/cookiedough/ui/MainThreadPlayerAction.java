package com.cookiebuild.cookiedough.ui;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

/** Dispatches a player mutation onto Paper's main thread with a late online guard. */
public final class MainThreadPlayerAction {
    private MainThreadPlayerAction() { }

    public static void dispatch(Plugin plugin, Player player, Runnable action) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        if (!plugin.isEnabled()) return;
        try {
            dispatch(task -> Bukkit.getScheduler().runTask(plugin, task),
                    () -> plugin.isEnabled() && player.isOnline(), action);
        } catch (IllegalPluginAccessException ignored) {
            // A result may race plugin shutdown. Dropping it is safer than mutating
            // gameplay state after the plugin has been disabled.
        }
    }

    static void dispatch(Consumer<Runnable> scheduler, BooleanSupplier eligible, Runnable action) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(eligible, "eligible");
        Objects.requireNonNull(action, "action");
        scheduler.accept(() -> {
            if (eligible.getAsBoolean()) action.run();
        });
    }
}
