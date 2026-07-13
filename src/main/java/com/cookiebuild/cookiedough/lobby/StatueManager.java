package com.cookiebuild.cookiedough.lobby;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.cookiedough.utils.SkinUtils;

public class StatueManager {
    private record StatueData(UUID playerId, String playerName, int wins) {
    }
    private final JavaPlugin plugin;
    private final Map<String, ArmorStand> statues = new HashMap<>();
    private final Map<String, ArmorStand> textDisplays = new HashMap<>();
    private final Map<String, Integer> winCounts = new HashMap<>();
    private final LeaderboardManager leaderboardManager;

    public StatueManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.leaderboardManager = new LeaderboardManager(plugin);
        startWeeklyUpdateTask();
    }

    public void createStatue(String gameMode, Location location) {
        CookieDough.getInstance().getLogger()
                .info("Creating statue for gameMode: " + gameMode + " at location: " + location);

        Location safeLocation = location.clone();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            StatueData data = loadStatueData(gameMode);
            if (data != null) {
                Bukkit.getScheduler().runTask(plugin, () -> renderStatue(gameMode, safeLocation, data));
            }
        });
    }

    private StatueData loadStatueData(String gameMode) {
        try {
            PlayerData topPlayer = PlayerStatsService.getTopPlayerThisWeekStatic(gameMode);
            if (topPlayer == null) {
                return null;
            }
            return new StatueData(topPlayer.getId(), topPlayer.getName(),
                    PlayerStatsService.getWinsThisWeekStatic(topPlayer.getId(), gameMode));
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Failed to load statue for " + gameMode + ": " + error.getMessage());
            return null;
        }
    }

    private void renderStatue(String gameMode, Location location, StatueData data) {
        removeStatue(gameMode);
        winCounts.put(gameMode, data.wins());
        ArmorStand statue = location.getWorld().spawn(location, ArmorStand.class);
        statue.setVisible(false);
        statue.setGravity(false);
        statue.setBasePlate(false);
        statue.setSmall(true);
        statue.setInvulnerable(true); // Prevent breaking
        statue.setDisabledSlots(org.bukkit.inventory.EquipmentSlot.values()); // Prevent item removal
        statue.setMetadata("statue", new FixedMetadataValue(plugin, true));
        statue.setMetadata("gameMode", new FixedMetadataValue(plugin, gameMode));

        // Set player head
        ItemStack head = SkinUtils.getPlayerHead(data.playerId());
        statue.getEquipment().setHelmet(head);

        statues.put(gameMode, statue);

        // Create floating text
        createFloatingText(gameMode, location.clone().add(0, 0.5, 0), data.playerName(), data.wins());
        leaderboardManager.createLeaderboard(gameMode, location.clone().add(0, 2, 0));
    }

    private void createFloatingText(String gameMode, Location location, String playerName, int wins) {
        ArmorStand textDisplay = location.getWorld().spawn(location, ArmorStand.class);
        textDisplay.setVisible(false);
        textDisplay.setGravity(false);
        textDisplay.customName(net.kyori.adventure.text.Component.text()
                .append(net.kyori.adventure.text.Component.text(playerName)
                        .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                .appendNewline()
                .append(net.kyori.adventure.text.Component.text("Top Player This Week: " + wins + " wins!")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
                .build());
        textDisplay.setCustomNameVisible(true);
        textDisplay.setMarker(true);
        textDisplay.setInvulnerable(true); // Prevent breaking
        textDisplay.setDisabledSlots(org.bukkit.inventory.EquipmentSlot.values()); // Prevent item removal
        textDisplay.setMetadata("statue", new FixedMetadataValue(plugin, true));
        textDisplay.setMetadata("gameMode", new FixedMetadataValue(plugin, gameMode));

        textDisplays.put(gameMode, textDisplay);
    }

    public void updateStatue(String gameMode) {
        if (!statues.containsKey(gameMode))
            return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            StatueData data = loadStatueData(gameMode);
            if (data != null) {
                Bukkit.getScheduler().runTask(plugin, () -> applyStatueUpdate(gameMode, data));
            }
        });
    }

    private void applyStatueUpdate(String gameMode, StatueData data) {
        winCounts.put(gameMode, data.wins());
        ArmorStand statue = statues.get(gameMode);
        if (statue != null) {
            statue.getEquipment().setHelmet(SkinUtils.getPlayerHead(data.playerId()));
        }

        // Update floating text
        ArmorStand textDisplay = textDisplays.get(gameMode);
        if (textDisplay != null) {
            textDisplay.customName(net.kyori.adventure.text.Component.text()
                    .append(net.kyori.adventure.text.Component.text(data.playerName())
                            .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                    .appendNewline()
                    .append(net.kyori.adventure.text.Component.text("Top Player This Week: " + data.wins() + " wins!")
                            .color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
                    .build());
        }
    }

    public void removeStatue(String gameMode) {
        if (statues.containsKey(gameMode)) {
            statues.get(gameMode).remove();
            statues.remove(gameMode);
        }
        if (textDisplays.containsKey(gameMode)) {
            textDisplays.get(gameMode).remove();
            textDisplays.remove(gameMode);
        }
        leaderboardManager.removeLeaderboard(gameMode);
        winCounts.remove(gameMode);
    }

    public int getWinCount(String gameMode) {
        return winCounts.getOrDefault(gameMode, 0);
    }

    private void startWeeklyUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Create a defensive copy to avoid ConcurrentModificationException
                for (String gameMode : new HashMap<>(statues).keySet()) {
                    try {
                        updateStatue(gameMode);
                        // Also update the leaderboard when updating the statue
                        leaderboardManager.updateLeaderboard(gameMode);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to update statue for " + gameMode + ": " + e.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20 * 60 * 60 * 24 * 7); // Update weekly
    }
}
