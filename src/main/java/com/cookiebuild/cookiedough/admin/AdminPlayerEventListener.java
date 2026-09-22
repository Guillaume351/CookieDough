package com.cookiebuild.cookiedough.admin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class AdminPlayerEventListener implements Listener {
    private final AdminBridge bridge;

    AdminPlayerEventListener(AdminBridge bridge) {
        this.bridge = bridge;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        bridge.publishPlayerEvent("player_joined", event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bridge.publishPlayerEvent("player_left", event.getPlayer());
    }
}
