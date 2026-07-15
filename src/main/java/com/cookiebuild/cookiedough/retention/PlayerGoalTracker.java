package com.cookiebuild.cookiedough.retention;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    public record GoalView(int dailyMatches, int dailyWins, int weeklyMatches, int weeklyWins,
            int weeklyEliminations, int achievements) {
        public boolean dailyComplete() {
            return dailyMatches >= 1 && dailyWins >= 1;
        }

        public boolean weeklyComplete() {
            return weeklyMatches >= 3 && weeklyWins >= 1 && weeklyEliminations >= 10;
        }
    }

    private record Reward(int coins, int experience, String game, String label) {
    }
    private static final class Progress {
        LocalDate day = utcToday();
        int dailyMatches;
        int dailyWins;
        String week = weekKey();
        int matches;
        int wins;
        int kills;
        LocalDate firstWinDate;
        final Set<String> achievements = new HashSet<>();
    }

    private final CookieDough plugin;
    private final File file;
    private final Map<UUID, Progress> progressByPlayer = new HashMap<>();
    private final PostgresGoalProgressRepository repository = new PostgresGoalProgressRepository();
    private boolean dirty;

    public PlayerGoalTracker(CookieDough plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "goals.yml");
        load();
        loadDatabaseAndImportYaml();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, 1200L, 1200L);
    }

    public synchronized void recordMatch(Player player, String game, boolean won, int kills) {
        recordMatch(player.getUniqueId(), game, won, kills);
    }

    public synchronized void recordMatch(UUID playerId, String game, boolean won, int kills) {
        Progress progress = progress(playerId);
        Map<String, Reward> rewards = new LinkedHashMap<>();
        resetDayIfNeeded(progress);
        resetWeekIfNeeded(progress);
        progress.dailyMatches++;
        progress.matches++;
        progress.kills += Math.max(0, kills);
        if (won) {
            progress.dailyWins++;
            progress.wins++;
            LocalDate today = utcToday();
            if (!today.equals(progress.firstWinDate)) {
                progress.firstWinDate = today;
            }
            rewards.put("first-win:" + today, new Reward(25, 30, game, "First win of the day"));
        }
        rewards.put("daily-match:" + progress.day, new Reward(15, 20, game, "Daily objective: play a match"));
        claimThreshold(rewards, progress.matches >= 3, "weekly-matches:" + progress.week, 50, 25, game,
                "Weekly objective: play 3 matches");
        claimThreshold(rewards, progress.wins >= 1, "weekly-win:" + progress.week, 50, 25, game,
                "Weekly objective: win a match");
        claimThreshold(rewards, progress.kills >= 10, "weekly-kills:" + progress.week, 75, 40, game,
                "Weekly objective: earn 10 eliminations");
        addAchievement(progress, rewards, "first_match", "First Match", 10, game);
        if (won) {
            addAchievement(progress, rewards, "first_win", "First Win", 20, game);
        }
        if (progress.kills >= 10) {
            addAchievement(progress, rewards, "ten_kills", "Ten Eliminations", 25, game);
        }
        dirty = true;
        persistAsync(playerId, progress);
        queueRewards(playerId, rewards);
    }

    public synchronized boolean grantAchievement(Player player, String key, String title, int coins) {
        Progress progress = progress(player.getUniqueId());
        boolean newlyGranted = progress.achievements.add(key);
        queueRewards(player.getUniqueId(), Map.of("achievement:" + key,
                new Reward(coins, 0, null, "Achievement unlocked: " + title)));
        if (!newlyGranted) {
            return false;
        }
        dirty = true;
        persistAsync(player.getUniqueId(), progress);
        return true;
    }

    public synchronized String summary(UUID playerId) {
        GoalView view = view(playerId);
        return "Daily — Match " + view.dailyMatches() + "/1, Win " + view.dailyWins()
                + "/1 | Weekly — Matches " + view.weeklyMatches() + "/3, Wins " + view.weeklyWins()
                + "/1, Eliminations " + view.weeklyEliminations() + "/10 | Achievements: " + view.achievements();
    }

    public synchronized GoalView view(UUID playerId) {
        Progress progress = progress(playerId);
        resetWeekIfNeeded(progress);
        resetDayIfNeeded(progress);
        return new GoalView(Math.min(progress.dailyMatches, 1), Math.min(progress.dailyWins, 1),
                Math.min(progress.matches, 3), Math.min(progress.wins, 1), Math.min(progress.kills, 10),
                progress.achievements.size());
    }

    public synchronized void shutdown() {
        saveIfDirty();
    }

    private void claimThreshold(Map<String, Reward> rewards, boolean completed, String key, int coins,
            int experience, String game, String label) {
        if (completed) {
            rewards.put(key, new Reward(coins, experience, game, label));
        }
    }

    private void addAchievement(Progress progress, Map<String, Reward> rewards, String key, String title, int coins,
            String game) {
        progress.achievements.add(key);
        rewards.put("achievement:" + key, new Reward(coins, 0, game, "Achievement unlocked: " + title));
    }

    private void queueRewards(UUID playerId, Map<String, Reward> rewards) {
        if (rewards.isEmpty()) {
            return;
        }
        Map<String, Reward> snapshot = Map.copyOf(rewards);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            MinigameProgressionService service = new MinigameProgressionService(null);
            Set<String> claimed = new HashSet<>();
            snapshot.forEach((key, reward) -> {
                boolean applied = reward.game() == null
                        ? service.claimGlobalReward(playerId, key, reward.coins())
                        : service.claimGoalReward(playerId, reward.game(), reward.experience(), reward.coins(), key);
                if (applied) claimed.add(key);
            });
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
                            + "+" + reward.coins() + " coins"
                            + (reward.experience() > 0 ? ChatColor.AQUA + " +" + reward.experience() + " XP" : ""));
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

    private void resetDayIfNeeded(Progress progress) {
        LocalDate today = utcToday();
        if (!today.equals(progress.day)) {
            progress.day = today;
            progress.dailyMatches = 0;
            progress.dailyWins = 0;
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
                String firstWin = yaml.getString(uuidText + ".first-win-date", "");
                progress.firstWinDate = firstWin.isBlank() ? null : LocalDate.parse(firstWin);
                progress.day = LocalDate.parse(yaml.getString(uuidText + ".day", utcToday().toString()));
                progress.dailyMatches = yaml.getInt(uuidText + ".daily-matches");
                progress.dailyWins = yaml.getInt(uuidText + ".daily-wins");
                progress.achievements.addAll(yaml.getStringList(uuidText + ".achievements"));
                progressByPlayer.put(playerId, progress);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring an invalid player record in goals.yml");
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
            yaml.set(path + ".day", progress.day.toString());
            yaml.set(path + ".daily-matches", progress.dailyMatches);
            yaml.set(path + ".daily-wins", progress.dailyWins);
            yaml.set(path + ".matches", progress.matches);
            yaml.set(path + ".wins", progress.wins);
            yaml.set(path + ".kills", progress.kills);
            yaml.set(path + ".first-win-date", progress.firstWinDate == null ? "" : progress.firstWinDate.toString());
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
        LocalDate date = utcToday();
        WeekFields iso = WeekFields.ISO;
        return date.get(iso.weekBasedYear()) + "-W" + String.format("%02d", date.get(iso.weekOfWeekBasedYear()));
    }

    private static LocalDate utcToday() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private void loadDatabaseAndImportYaml() {
        try {
            Map<UUID, GoalProgressSnapshot> database = repository.loadAll();
            database.forEach((playerId, snapshot) -> progressByPlayer.put(playerId, fromSnapshot(snapshot)));
            progressByPlayer.forEach((playerId, progress) -> {
                if (!database.containsKey(playerId)) {
                    persistAsync(playerId, progress);
                }
            });
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Could not load goal progress from PostgreSQL; using the local fallback: "
                    + error.getMessage());
        }
    }

    private void persistAsync(UUID playerId, Progress progress) {
        GoalProgressSnapshot snapshot = snapshot(progress);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.upsert(playerId, snapshot);
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Could not sync a player goal record to PostgreSQL: " + error.getMessage());
            }
        });
    }

    private static GoalProgressSnapshot snapshot(Progress progress) {
        return new GoalProgressSnapshot(progress.day, progress.dailyMatches, progress.dailyWins,
                progress.firstWinDate, progress.week, progress.matches, progress.wins, progress.kills,
                progress.achievements);
    }

    private static Progress fromSnapshot(GoalProgressSnapshot snapshot) {
        Progress progress = new Progress();
        progress.day = snapshot.day();
        progress.dailyMatches = snapshot.dailyMatches();
        progress.dailyWins = snapshot.dailyWins();
        progress.firstWinDate = snapshot.firstWinDate();
        progress.week = snapshot.week();
        progress.matches = snapshot.weeklyMatches();
        progress.wins = snapshot.weeklyWins();
        progress.kills = snapshot.weeklyKills();
        progress.achievements.addAll(snapshot.achievements());
        return progress;
    }
}
