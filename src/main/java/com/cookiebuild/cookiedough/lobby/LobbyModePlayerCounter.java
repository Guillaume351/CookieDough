package com.cookiebuild.cookiedough.lobby;

import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.activity.ActivityRegistry;
import com.cookiebuild.cookiedough.activity.PersistentActivity;
import com.cookiebuild.cookiedough.game.GameManager;

/** Supplies the live, mode-wide activity count shown above lobby selectors. */
final class LobbyModePlayerCounter {
    private LobbyModePlayerCounter() {
    }

    static int forMinigame(String gameName) {
        return GameManager.getOnlineGamePlayerCount(gameName);
    }

    static int forPersistentActivity(String activityName) {
        return countOwnedOnline(ActivityRegistry.find(activityName), Bukkit.getOnlinePlayers());
    }

    static int countOwnedOnline(PersistentActivity activity, Collection<? extends Player> onlinePlayers) {
        if (activity == null || onlinePlayers == null || onlinePlayers.isEmpty()) {
            return 0;
        }
        return (int) onlinePlayers.stream()
                .filter(player -> player != null && player.isOnline())
                .map(Player::getUniqueId)
                .distinct()
                .filter(activity::owns)
                .count();
    }
}
