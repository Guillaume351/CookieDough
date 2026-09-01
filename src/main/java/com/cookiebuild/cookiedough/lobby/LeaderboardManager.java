package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.service.PlayerStatsService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class LeaderboardManager {
    private record LeaderboardEntry(String name, int wins) {
    }
    private final JavaPlugin plugin;
    private final PluginTaskDispatcher tasks;
    private final Map<String, List<ArmorStand>> leaderboardLines = new HashMap<>();
    private final Map<String, Location> leaderboardLocations = new HashMap<>();
    private final Map<String, Long> refreshGenerations = new HashMap<>();
    private BukkitTask monthlyUpdateTask;
    private static final double LINE_SPACING = 0.3; // Space between lines

    public LeaderboardManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tasks = new PluginTaskDispatcher(plugin);
        startMonthlyUpdateTask();
    }

    public void createLeaderboard(String gameMode, Location baseLocation) {
        leaderboardLocations.put(gameMode, baseLocation.clone());
        requestRefresh(gameMode);
    }

    private void requestRefresh(String gameMode) {
        Location baseLocation = leaderboardLocations.get(gameMode);
        if (baseLocation == null) {
            return;
        }
        long generation = refreshGenerations.merge(gameMode, 1L, Long::sum);
        tasks.runAsync(() -> {
            try {
                List<LeaderboardEntry> entries = loadLeaderboard(gameMode);
                tasks.runSync(() -> {
                    if (refreshGenerations.getOrDefault(gameMode, 0L) == generation
                            && leaderboardLocations.containsKey(gameMode)) {
                        renderLeaderboard(gameMode, baseLocation, entries);
                    }
                });
            } catch (RuntimeException error) {
                if (tasks.isActive()) {
                    plugin.getLogger().warning(
                            "Failed to load leaderboard for " + gameMode + ": " + error.getMessage());
                }
            }
        });
    }

    private void renderLeaderboard(String gameMode, Location baseLocation, List<LeaderboardEntry> entries) {
        removeRenderedLines(gameMode);
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
        stand.setPersistent(true);
        LobbyEntityOwnership.mark(plugin, stand, "leaderboard");
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
        requestRefresh(gameMode);
    }

    public void removeLeaderboard(String gameMode) {
        leaderboardLocations.remove(gameMode);
        refreshGenerations.merge(gameMode, 1L, Long::sum);
        removeRenderedLines(gameMode);
    }

    private void removeRenderedLines(String gameMode) {
        List<ArmorStand> lines = leaderboardLines.get(gameMode);
        if (lines != null) {
            lines.forEach(ArmorStand::remove);
            leaderboardLines.remove(gameMode);
        }
    }

    private void startMonthlyUpdateTask() {
        monthlyUpdateTask = new BukkitRunnable() {
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

    public void shutdown() {
        tasks.close();
        if (monthlyUpdateTask != null) {
            monthlyUpdateTask.cancel();
            monthlyUpdateTask = null;
        }
        leaderboardLocations.clear();
        refreshGenerations.replaceAll((gameMode, generation) -> generation + 1);
        for (String gameMode : new ArrayList<>(leaderboardLines.keySet())) {
            removeRenderedLines(gameMode);
        }
    }
}
