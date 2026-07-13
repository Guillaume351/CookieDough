package com.cookiebuild.cookiedough.retention;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;

/** Durable lightweight goals; coin claims are independently idempotent in the database. */
public final class PlayerGoalTracker {
    private record Reward(int coins, String label) {
    }
    private static final class Progress {
        String week = weekKey();
        int matches;
        int wins;
        int kills;
        String firstWinDate = "";
        final Set<String> achievements = new HashSet<>();
    }

    private final CookieDough plugin;
    private final File file;
    private final Map<UUID, Progress> progressByPlayer = new HashMap<>();
    private boolean dirty;

    public PlayerGoalTracker(CookieDough plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "goals.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 1200L, 1200L);
    }

    public synchronized void recordMatch(Player player, String game, boolean won, int kills) {
        recordMatch(player.getUniqueId(), game, won, kills);
    }

    public synchronized void recordMatch(UUID playerId, String game, boolean won, int kills) {
        Progress progress = progress(playerId);
        Map<String, Reward> rewards = new LinkedHashMap<>();
        resetWeekIfNeeded(progress);
        progress.matches++;
        progress.kills += Math.max(0, kills);
        if (won) {
            progress.wins++;
            String today = LocalDate.now().toString();
            if (!today.equals(progress.firstWinDate)) {
                progress.firstWinDate = today;
            }
            rewards.put("first-win:" + today, new Reward(25, "First win of the day"));
        }
        claimThreshold(rewards, progress.matches >= 3, "weekly-matches:" + progress.week, 50,
                "Weekly objective: play 3 matches");
        claimThreshold(rewards, progress.wins >= 1, "weekly-win:" + progress.week, 50,
                "Weekly objective: win a match");
        claimThreshold(rewards, progress.kills >= 10, "weekly-kills:" + progress.week, 75,
                "Weekly objective: earn 10 eliminations");
        addAchievement(progress, rewards, "first_match", "First Match", 10);
        if (won) {
            addAchievement(progress, rewards, "first_win", "First Win", 20);
        }
        if (progress.kills >= 10) {
            addAchievement(progress, rewards, "ten_kills", "Ten Eliminations", 25);
        }
        dirty = true;
        queueRewards(playerId, rewards);
    }

    public synchronized boolean grantAchievement(Player player, String key, String title, int coins) {
        Progress progress = progress(player.getUniqueId());
        boolean newlyGranted = progress.achievements.add(key);
        queueRewards(player.getUniqueId(), Map.of("achievement:" + key,
                new Reward(coins, "Achievement unlocked: " + title)));
        if (!newlyGranted) {
            return false;
        }
        dirty = true;
        return true;
    }

    public synchronized String summary(UUID playerId) {
        Progress progress = progress(playerId);
        resetWeekIfNeeded(progress);
        return "Weekly goals — Matches " + Math.min(progress.matches, 3) + "/3, Wins "
                + Math.min(progress.wins, 1) + "/1, Eliminations " + Math.min(progress.kills, 10)
                + "/10 | Achievements: " + progress.achievements.size();
    }

    public synchronized void shutdown() {
        saveIfDirty();
    }

    private void claimThreshold(Map<String, Reward> rewards, boolean completed, String key, int coins, String label) {
        if (completed) {
            rewards.put(key, new Reward(coins, label));
        }
    }

    private void addAchievement(Progress progress, Map<String, Reward> rewards, String key, String title, int coins) {
        progress.achievements.add(key);
        rewards.put("achievement:" + key, new Reward(coins, "Achievement unlocked: " + title));
    }

    private void queueRewards(UUID playerId, Map<String, Reward> rewards) {
        if (rewards.isEmpty()) {
            return;
        }
        Map<String, Reward> snapshot = Map.copyOf(rewards);
        Map<String, Integer> coinValues = new LinkedHashMap<>();
        snapshot.forEach((key, reward) -> coinValues.put(key, reward.coins()));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<String> claimed = new MinigameProgressionService(null).claimGlobalRewards(playerId, coinValues);
            for (String key : claimed) {
                FunnelTelemetry.record(playerId, FunnelTelemetry.Event.REWARD_CLAIMED,
                        "source=" + key + " coins=" + snapshot.get(key).coins());
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player online = plugin.getServer().getPlayer(playerId);
                if (online == null) {
                    return;
                }
                for (String key : claimed) {
                    Reward reward = snapshot.get(key);
                    online.sendMessage(ChatColor.GREEN + reward.label() + "! " + ChatColor.GOLD
                            + "+" + reward.coins() + " coins");
                }
            });
        });
    }

    private Progress progress(UUID playerId) {
        return progressByPlayer.computeIfAbsent(playerId, ignored -> new Progress());
    }

    private void resetWeekIfNeeded(Progress progress) {
        String currentWeek = weekKey();
        if (!currentWeek.equals(progress.week)) {
            progress.week = currentWeek;
            progress.matches = 0;
            progress.wins = 0;
            progress.kills = 0;
            dirty = true;
        }
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String uuidText : yaml.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(uuidText);
                Progress progress = new Progress();
                progress.week = yaml.getString(uuidText + ".week", weekKey());
                progress.matches = yaml.getInt(uuidText + ".matches");
                progress.wins = yaml.getInt(uuidText + ".wins");
                progress.kills = yaml.getInt(uuidText + ".kills");
                progress.firstWinDate = yaml.getString(uuidText + ".first-win-date", "");
                progress.achievements.addAll(yaml.getStringList(uuidText + ".achievements"));
                progressByPlayer.put(playerId, progress);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid player key in goals.yml: " + uuidText);
            }
        }
    }

    private synchronized void saveIfDirty() {
        if (!dirty) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        progressByPlayer.forEach((playerId, progress) -> {
            String path = playerId.toString();
            yaml.set(path + ".week", progress.week);
            yaml.set(path + ".matches", progress.matches);
            yaml.set(path + ".wins", progress.wins);
            yaml.set(path + ".kills", progress.kills);
            yaml.set(path + ".first-win-date", progress.firstWinDate);
            yaml.set(path + ".achievements", progress.achievements.stream().sorted().toList());
        });
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException error) {
            plugin.getLogger().warning("Could not save goals.yml: " + error.getMessage());
        }
    }

    private static String weekKey() {
        LocalDate date = LocalDate.now();
        WeekFields iso = WeekFields.ISO;
        return date.get(iso.weekBasedYear()) + "-W" + String.format("%02d", date.get(iso.weekOfWeekBasedYear()));
    }
}
