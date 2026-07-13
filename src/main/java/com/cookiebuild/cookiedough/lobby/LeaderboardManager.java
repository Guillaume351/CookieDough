package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.service.PlayerStatsService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class LeaderboardManager {
    private record LeaderboardEntry(String name, int wins) {
    }
    private final JavaPlugin plugin;
    private final Map<String, List<ArmorStand>> leaderboardLines = new HashMap<>();
    private static final double LINE_SPACING = 0.3; // Space between lines

    public LeaderboardManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startMonthlyUpdateTask();
    }

    public void createLeaderboard(String gameMode, Location baseLocation) {
        removeLeaderboard(gameMode);
        Location safeLocation = baseLocation.clone();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<LeaderboardEntry> entries = loadLeaderboard(gameMode);
                Bukkit.getScheduler().runTask(plugin, () -> renderLeaderboard(gameMode, safeLocation, entries));
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Failed to load leaderboard for " + gameMode + ": " + error.getMessage());
            }
        });
    }

    private void renderLeaderboard(String gameMode, Location baseLocation, List<LeaderboardEntry> entries) {
        List<ArmorStand> lines = new ArrayList<>();
        Location titleLoc = baseLocation.clone().add(0, 2.5, 0);
        Component title = entries.isEmpty()
                ? Component.text().append(Component.text(gameMode).color(NamedTextColor.AQUA))
                        .append(Component.text(" - No Data").color(NamedTextColor.GRAY)).build()
                : Component.text().append(Component.text(gameMode).color(NamedTextColor.AQUA))
                        .append(Component.text(" - Monthly Leaderboard").color(NamedTextColor.GOLD))
                        .decorate(TextDecoration.BOLD).build();
        lines.add(spawnHologram(titleLoc, title));
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            Location lineLoc = titleLoc.clone().subtract(0, (i + 1) * LINE_SPACING, 0);
            Component text = Component.text()
                    .append(Component.text("#" + (i + 1) + " ")
                            .color(i < 3 ? NamedTextColor.GOLD : NamedTextColor.GRAY))
                    .append(Component.text(entry.name()).color(NamedTextColor.YELLOW))
                    .append(Component.text(" - " + entry.wins() + " wins").color(NamedTextColor.WHITE)).build();
            lines.add(spawnHologram(lineLoc, text));
        }
        leaderboardLines.put(gameMode, lines);
    }

    private ArmorStand spawnHologram(Location location, Component text) {
        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(true);
        stand.customName(text);
        stand.setDisabledSlots(org.bukkit.inventory.EquipmentSlot.values());
        stand.setMetadata("leaderboard", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        return stand;
    }

    private List<LeaderboardEntry> loadLeaderboard(String gameMode) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (PlayerData player : PlayerStatsService.getTopPlayersThisMonthStatic(gameMode, 10)) {
            entries.add(new LeaderboardEntry(player.getName(),
                    PlayerStatsService.getWinsThisMonthStatic(player.getId(), gameMode)));
        }
        return entries;
    }

    public void updateLeaderboard(String gameMode) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<LeaderboardEntry> entries = loadLeaderboard(gameMode);
                Bukkit.getScheduler().runTask(plugin, () -> applyLeaderboard(gameMode, entries));
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Failed to load leaderboard for " + gameMode + ": " + error.getMessage());
            }
        });
    }

    private void applyLeaderboard(String gameMode, List<LeaderboardEntry> entries) {
        List<ArmorStand> lines = leaderboardLines.get(gameMode);
        if (lines == null || lines.size() < 2) {
            return;
        }
        for (int i = 0; i < Math.min(entries.size(), lines.size() - 1); i++) {
            LeaderboardEntry entry = entries.get(i);
            lines.get(i + 1).customName(Component.text()
                    .append(Component.text("#" + (i + 1) + " ")
                            .color(i < 3 ? NamedTextColor.GOLD : NamedTextColor.GRAY))
                    .append(Component.text(entry.name()).color(NamedTextColor.YELLOW))
                    .append(Component.text(" - " + entry.wins() + " wins").color(NamedTextColor.WHITE))
                    .build());
        }
    }

    public void removeLeaderboard(String gameMode) {
        List<ArmorStand> lines = leaderboardLines.get(gameMode);
        if (lines != null) {
            lines.forEach(ArmorStand::remove);
            leaderboardLines.remove(gameMode);
        }
    }

    private void startMonthlyUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Get a snapshot of the current game modes to avoid concurrent modification
                List<String> gameModes = new ArrayList<>(leaderboardLines.keySet());
                for (String gameMode : gameModes) {
                    // Each game mode update is already run async in updateLeaderboard
                    updateLeaderboard(gameMode);
                }
            }
        }.runTaskTimer(plugin, 0, 20 * 60 * 60); // Update every hour
    }
}
