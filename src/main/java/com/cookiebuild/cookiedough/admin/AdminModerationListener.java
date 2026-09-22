package com.cookiebuild.cookiedough.admin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import com.cookiebuild.cookiedough.admin.moderation.ModerationAction;
import com.cookiebuild.cookiedough.admin.moderation.ModerationService;

final class AdminModerationListener implements Listener {
    private final ModerationService moderation;
    private final AdminServerState serverState;

    AdminModerationListener(ModerationService moderation, AdminServerState serverState) {
        this.moderation = moderation;
        this.serverState = serverState;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (serverState.maintenance()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "Cookie Build is in maintenance. Please try again shortly.");
            return;
        }
        moderation.refreshForLogin(event.getUniqueId()).ifPresent(action -> event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage(action)));
    }

    private static String banMessage(ModerationAction action) {
        String message = "Banned from Cookie Build: " + action.reason();
        if (action.expiresAt() != null) message += " (until " + action.expiresAt() + ")";
        return message;
    }
}
