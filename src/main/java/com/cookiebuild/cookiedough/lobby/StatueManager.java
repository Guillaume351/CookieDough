package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.cookiedough.utils.SkinUtils;

/** Manages the compact, cross-edition champion showcase beside game NPCs. */
public class StatueManager {
    private static final long WEEKLY_REFRESH_TICKS = 20L * 60L * 60L * 24L * 7L;
    private static volatile StatueManager active;

    private record StatueData(UUID playerId, String playerName, int wins) {
    }

    private record StatueLoad(boolean successful, StatueData data) {
        static StatueLoad success(StatueData data) {
            return new StatueLoad(true, data);
        }

        static StatueLoad failure() {
            return new StatueLoad(false, null);
        }
    }

    private final JavaPlugin plugin;
    private final PluginTaskDispatcher tasks;
    private final boolean championHeadsEnabled;
    private final LeaderboardManager leaderboardManager;
    private final Map<String, ArmorStand> statues = new HashMap<>();
    private final Map<String, Integer> winCounts = new HashMap<>();
    private final Map<String, Location> statueLocations = new HashMap<>();
    private final Map<String, Long> refreshGenerations = new HashMap<>();
    private BukkitTask weeklyUpdateTask;

    public StatueManager(JavaPlugin plugin, boolean championHeadsEnabled,
            boolean leaderboardPanelsEnabled) {
        this.plugin = plugin;
        this.tasks = new PluginTaskDispatcher(plugin);
        this.championHeadsEnabled = championHeadsEnabled;
        this.leaderboardManager = leaderboardPanelsEnabled ? new LeaderboardManager(plugin) : null;
        active = this;
        if (championHeadsEnabled) {
            startWeeklyUpdateTask();
        }
    }

    /** Refreshes enabled showcase elements after a completed match. */
    public static void refreshAfterMatch(String gameMode) {
        StatueManager manager = active;
        if (manager == null || gameMode == null) {
            return;
        }
        manager.updateStatue(gameMode);
        if (manager.leaderboardManager != null) {
            manager.leaderboardManager.updateLeaderboard(gameMode);
        }
    }

    public void createStatue(String gameMode, Location location) {
        Location safeLocation = location.clone();
        statueLocations.put(gameMode, safeLocation);
        if (leaderboardManager != null) {
            leaderboardManager.createLeaderboard(gameMode, safeLocation.clone().add(0, 2, 0));
        }
        if (championHeadsEnabled) {
            requestHeadRefresh(gameMode, safeLocation);
        }
    }

    private StatueLoad loadStatueData(String gameMode) {
        try {
            PlayerData topPlayer = PlayerStatsService.getTopPlayerThisWeekStatic(gameMode);
            if (topPlayer == null) {
                return StatueLoad.success(null);
            }
            return StatueLoad.success(new StatueData(topPlayer.getId(), topPlayer.getName(),
                    PlayerStatsService.getWinsThisWeekStatic(topPlayer.getId(), gameMode)));
        } catch (RuntimeException error) {
            if (tasks.isActive()) {
                plugin.getLogger().warning("Failed to load champion head for " + gameMode + ": "
                        + error.getMessage());
            }
            return StatueLoad.failure();
        }
    }

    private void requestHeadRefresh(String gameMode, Location location) {
        long generation = refreshGenerations.merge(gameMode, 1L, Long::sum);
        tasks.runAsync(() -> {
            StatueLoad load = loadStatueData(gameMode);
            if (!load.successful()) {
                return;
            }
            tasks.runSync(() -> {
                if (refreshGenerations.getOrDefault(gameMode, 0L) != generation
                        || !statueLocations.containsKey(gameMode)) {
                    return;
                }
                if (load.data() == null) {
                    removeStatueEntity(gameMode);
                } else if (statues.containsKey(gameMode)) {
                    applyStatueUpdate(gameMode, load.data());
                } else {
                    renderStatue(gameMode, location, load.data());
                }
            });
        });
    }

    private void renderStatue(String gameMode, Location location, StatueData data) {
        removeStatueEntity(gameMode);
        ArmorStand statue = location.getWorld().spawn(location, ArmorStand.class);
        statue.setVisible(false);
        statue.setGravity(false);
        statue.setBasePlate(false);
        statue.setSmall(true);
        statue.setSilent(true);
        statue.setCollidable(false);
        statue.setInvulnerable(true);
        statue.setPersistent(true);
        statue.setDisabledSlots(org.bukkit.inventory.EquipmentSlot.values());
        statue.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "champion_head"), PersistentDataType.STRING, gameMode);

        ItemStack head = SkinUtils.getPlayerHead(data.playerId());
        statue.getEquipment().setHelmet(head);
        statue.customName(LobbyDisplayText.championHead(data.playerName(), data.wins()));
        statue.setCustomNameVisible(true);

        statues.put(gameMode, statue);
        winCounts.put(gameMode, data.wins());
    }

    public void updateStatue(String gameMode) {
        if (!championHeadsEnabled) {
            return;
        }
        Location location = statueLocations.get(gameMode);
        if (location != null) {
            requestHeadRefresh(gameMode, location);
        }
    }

    private void applyStatueUpdate(String gameMode, StatueData data) {
        ArmorStand statue = statues.get(gameMode);
        if (statue == null || !statue.isValid()) {
            Location location = statueLocations.get(gameMode);
            if (location != null) {
                renderStatue(gameMode, location, data);
            }
            return;
        }
        statue.getEquipment().setHelmet(SkinUtils.getPlayerHead(data.playerId()));
        statue.customName(LobbyDisplayText.championHead(data.playerName(), data.wins()));
        winCounts.put(gameMode, data.wins());
    }

    public void removeStatue(String gameMode) {
        statueLocations.remove(gameMode);
        refreshGenerations.merge(gameMode, 1L, Long::sum);
        removeStatueEntity(gameMode);
        if (leaderboardManager != null) {
            leaderboardManager.removeLeaderboard(gameMode);
        }
    }

    private void removeStatueEntity(String gameMode) {
        ArmorStand statue = statues.remove(gameMode);
        if (statue != null) {
            statue.remove();
        }
        winCounts.remove(gameMode);
    }

    public int getWinCount(String gameMode) {
        return winCounts.getOrDefault(gameMode, 0);
    }

    private void startWeeklyUpdateTask() {
        weeklyUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (String gameMode : new ArrayList<>(statueLocations.keySet())) {
                    try {
                        updateStatue(gameMode);
                    } catch (RuntimeException error) {
                        plugin.getLogger().warning("Failed to update champion head for " + gameMode + ": "
                                + error.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, WEEKLY_REFRESH_TICKS, WEEKLY_REFRESH_TICKS);
    }

    public void shutdown() {
        tasks.close();
        if (weeklyUpdateTask != null) {
            weeklyUpdateTask.cancel();
            weeklyUpdateTask = null;
        }
        if (leaderboardManager != null) {
            leaderboardManager.shutdown();
        }
        refreshGenerations.replaceAll((gameMode, generation) -> generation + 1L);
        for (String gameMode : new ArrayList<>(statues.keySet())) {
            removeStatueEntity(gameMode);
        }
        statueLocations.clear();
        if (active == this) {
            active = null;
        }
    }
}
