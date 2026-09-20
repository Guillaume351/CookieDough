package com.cookiebuild.cookiedough.cosmetics;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;

/** Bridges persistence completion back onto Paper's main thread. */
@FunctionalInterface
public interface VictoryEffectDispatcher {
    void dispatch(UUID winnerId);

    default void dispatchAll(Collection<UUID> winnerIds) {
        if (winnerIds == null) return;
        winnerIds.stream().filter(Objects::nonNull).distinct().forEach(winnerId -> {
            try {
                dispatch(winnerId);
            } catch (RuntimeException error) {
                // The match transaction already committed; visual delivery cannot fail its caller.
                java.util.logging.Logger.getLogger(VictoryEffectDispatcher.class.getName())
                        .warning("Could not dispatch cosmetic victory effect for " + winnerId
                                + ": " + error.getMessage());
            }
        });
    }

    static VictoryEffectDispatcher live() {
        return winnerId -> {
            CookieDough plugin = CookieDough.getInstance();
            if (plugin == null || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player winner = Bukkit.getPlayer(winnerId);
                if (winner != null && winner.isOnline()) plugin.playCosmeticVictoryEffect(winner);
            });
        };
    }
}
