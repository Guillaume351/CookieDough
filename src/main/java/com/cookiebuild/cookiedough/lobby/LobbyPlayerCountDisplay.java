package com.cookiebuild.cookiedough.lobby;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.game.GameManager;

final class LobbyPlayerCountDisplay {
    private final JavaPlugin plugin;
    private final Location location;
    private ArmorStand display;
    private BukkitTask refreshTask;
    private int displayedPlayerCount = -1;
    private int displayedGamePlayerCount = -1;

    LobbyPlayerCountDisplay(JavaPlugin plugin, Location location) {
        this.plugin = plugin;
        this.location = location.clone();
        spawnDisplay();
        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                refresh();
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void spawnDisplay() {
        display = location.getWorld().spawn(location, ArmorStand.class);
        display.setVisible(false);
        display.setGravity(false);
        display.setMarker(true);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setPersistent(true);
        display.setCustomNameVisible(true);
        display.setDisabledSlots(EquipmentSlot.values());
        displayedPlayerCount = -1;
        displayedGamePlayerCount = -1;
    }

    private void refresh() {
        if (display == null || !display.isValid()) {
            spawnDisplay();
        }
        int playerCount = Bukkit.getOnlinePlayers().size();
        int gamePlayerCount = GameManager.getOnlineGamePlayerCount();
        if (playerCount != displayedPlayerCount || gamePlayerCount != displayedGamePlayerCount) {
            display.customName(LobbyDisplayText.onlinePlayers(playerCount, gamePlayerCount));
            displayedPlayerCount = playerCount;
            displayedGamePlayerCount = gamePlayerCount;
        }
    }

    void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (display != null) {
            display.remove();
            display = null;
        }
    }
}
