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
    private final JavaPlugin plugin;
    private final Map<String, List<ArmorStand>> leaderboardLines = new HashMap<>();
    private static final double LINE_SPACING = 0.3; // Space between lines

    public LeaderboardManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startMonthlyUpdateTask();
    }

    public void createLeaderboard(String gameMode, Location baseLocation) {
        plugin.getLogger().info("=== CREATING LEADERBOARD DEBUG ===");
        plugin.getLogger().info("GameMode: " + gameMode);
        plugin.getLogger().info("Location: " + baseLocation);

        // Remove existing leaderboard if any
        removeLeaderboard(gameMode);

        // Use static methods for better resource management
        try {
            plugin.getLogger().info("Fetching top players for: " + gameMode);
            List<PlayerData> topPlayers = PlayerStatsService.getTopPlayersThisMonthStatic(gameMode, 10);
            plugin.getLogger().info("Found " + topPlayers.size() + " top players");

            List<ArmorStand> lines = new ArrayList<>();

            // Title
            Location titleLoc = baseLocation.clone().add(0, 2.5, 0); // Above the statue
            plugin.getLogger().info("Creating title at: " + titleLoc);
            ArmorStand titleStand = spawnHologram(titleLoc, Component.text()
                    .append(Component.text(gameMode).color(NamedTextColor.AQUA))
                    .append(Component.text(" - Monthly Leaderboard").color(NamedTextColor.GOLD))
                    .decorate(TextDecoration.BOLD)
                    .build());
            lines.add(titleStand);
            plugin.getLogger().info("Title created successfully");

            // Player entries
            for (int i = 0; i < topPlayers.size(); i++) {
                PlayerData player = topPlayers.get(i);
                plugin.getLogger().info("Processing player " + i + ": " + player.getName());

                int wins = PlayerStatsService.getWinsThisMonthStatic(player.getId(), gameMode);
                plugin.getLogger().info("Player " + player.getName() + " has " + wins + " wins");

                Location lineLoc = titleLoc.clone().subtract(0, (i + 1) * LINE_SPACING, 0);
                plugin.getLogger().info("Creating line " + i + " at: " + lineLoc);

                Component text = Component.text()
                        .append(Component.text("#" + (i + 1) + " ")
                                .color(i < 3 ? NamedTextColor.GOLD : NamedTextColor.GRAY))
                        .append(Component.text(player.getName()).color(NamedTextColor.YELLOW))
                        .append(Component.text(" - " + wins + " wins").color(NamedTextColor.WHITE))
                        .build();

                ArmorStand line = spawnHologram(lineLoc, text);
                lines.add(line);
                plugin.getLogger().info("Line " + i + " created successfully");
            }

            leaderboardLines.put(gameMode, lines);
            plugin.getLogger().info("Leaderboard created successfully with " + lines.size() + " lines");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create leaderboard for " + gameMode + ": " + e.getMessage());
            e.printStackTrace();
        }
        plugin.getLogger().info("=== END CREATING LEADERBOARD DEBUG ===");
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

    private void updateLeaderboardWithRetry(String gameMode, int maxRetries) {
        int retryCount = 0;
        long waitTime = 1000; // Start with 1 second

        while (retryCount < maxRetries) {
            try {
                List<PlayerData> topPlayers = PlayerStatsService.getTopPlayersThisMonthStatic(gameMode, 10);
                List<ArmorStand> lines = leaderboardLines.get(gameMode);

                if (lines == null || lines.size() < 2) {
                    return; // Title + at least one entry
                }

                // Skip title (index 0)
                for (int i = 0; i < Math.min(topPlayers.size(), lines.size() - 1); i++) {
                    PlayerData player = topPlayers.get(i);
                    int wins = PlayerStatsService.getWinsThisMonthStatic(player.getId(), gameMode);

                    ArmorStand line = lines.get(i + 1);
                    Component text = Component.text()
                            .append(Component.text("#" + (i + 1) + " ")
                                    .color(i < 3 ? NamedTextColor.GOLD : NamedTextColor.GRAY))
                            .append(Component.text(player.getName()).color(NamedTextColor.YELLOW))
                            .append(Component.text(" - " + wins + " wins").color(NamedTextColor.WHITE))
                            .build();

                    line.customName(text);
                }
                // If we get here, the update was successful
                return;
            } catch (Exception e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    plugin.getLogger().severe("Failed to update leaderboard for " + gameMode + " after " + maxRetries
                            + " attempts: " + e.getMessage());
                    return;
                }
                plugin.getLogger().warning("Failed to update leaderboard for " + gameMode + ", attempt " + retryCount
                        + " of " + maxRetries + ": " + e.getMessage());
                try {
                    Thread.sleep(waitTime);
                    waitTime *= 2; // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public void updateLeaderboard(String gameMode) {
        // Run the update with retries in an async task to avoid blocking
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            updateLeaderboardWithRetry(gameMode, 3);
        });
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
