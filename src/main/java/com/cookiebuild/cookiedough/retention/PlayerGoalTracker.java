package com.cookiebuild.cookiedough.retention;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.utils.LocaleManager;

/** Durable lightweight goals; coin claims are independently idempotent in the database. */
public final class PlayerGoalTracker {
    /**
     * Snapshot shown by the hub, /goals and game summaries. {@code weeklyFinished}
     * counts matches toward the "finish 5 matches" weekly objective.
     */
    public record GoalView(int dailyMatches, int dailyWins, int weeklyMatches, int weeklyWins,
            int weeklyFinished, int achievements) {
        public boolean dailyComplete() {
            return dailyMatches >= 1 && dailyWins >= 1;
        }

        public boolean weeklyComplete() {
            return weeklyMatches >= GoalRules.WEEKLY_MATCHES_TARGET
                    && weeklyWins >= GoalRules.WEEKLY_WINS_TARGET
                    && weeklyFinished >= GoalRules.WEEKLY_FINISHED_TARGET;
        }
    }

    private record Reward(int coins, int experience, String game, String labelKey) {
    }
    private static final class Progress {
        LocalDate day = parisToday();
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
        loadDatabaseAndMergeYaml();
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
            LocalDate today = parisToday();
            if (!today.equals(progress.firstWinDate)) {
                progress.firstWinDate = today;
            }
            rewards.put("first-win:" + today, new Reward(GoalRules.DAILY_WIN_COINS, GoalRules.DAILY_WIN_XP,
                    game, "goals.daily.first_win"));
        }
        rewards.put("daily-match:" + progress.day, new Reward(GoalRules.DAILY_MATCH_COINS,
                GoalRules.DAILY_MATCH_XP, game, "goals.daily.play_match"));
        for (GoalRules.Objective objective : GoalRules.completedWeekly(progress.matches, progress.wins)) {
            rewards.put(objective.key() + ":" + progress.week, new Reward(objective.coins(),
                    objective.experience(), game, objective.labelKey()));
        }
        for (GoalRules.Achievement achievement : GoalRules.matchAchievements(game, won, progress.kills)) {
            addAchievement(progress, rewards, achievement, game);
        }
        dirty = true;
        persistAsync(playerId, progress);
        queueRewards(playerId, rewards);
        scheduleLifetimeMilestones(playerId);
    }

    /**
     * Grants a named achievement. {@code title} is only a fallback for callers
     * whose key has no localized {@code goals.achievement.<key>} message.
     */
    public synchronized boolean grantAchievement(Player player, String key, String title, int coins) {
        Progress progress = progress(player.getUniqueId());
        boolean newlyGranted = progress.achievements.add(key);
        queueRewards(player.getUniqueId(), Map.of("achievement:" + key,
                new Reward(coins, 0, null, "goals.achievement." + key)));
        if (!newlyGranted) {
            return false;
        }
        dirty = true;
        persistAsync(player.getUniqueId(), progress);
        return true;
    }

    /** True when the player has unlocked this achievement (never creates progress). */
    public synchronized boolean hasAchievement(UUID playerId, String key) {
        Progress progress = playerId == null ? null : progressByPlayer.get(playerId);
        return progress != null && progress.achievements.contains(key);
    }

    /** Localized one-line summary for the online player's locale (English fallback). */
    public String summary(UUID playerId) {
        Player online = plugin.getServer().getPlayer(playerId);
        return summary(playerId, online == null ? Locale.ENGLISH : online.locale());
    }

    public synchronized String summary(UUID playerId, Locale locale) {
        GoalView view = view(playerId);
        return LocaleManager.getMessage("goals.summary", locale, view.dailyMatches(), view.dailyWins(),
                view.weeklyMatches(), view.weeklyWins(), view.weeklyFinished(), view.achievements());
    }

    /** Multi-line localized /goals output; plain text so it works on Bedrock. */
    public synchronized List<String> describe(UUID playerId, Locale locale) {
        GoalView view = view(playerId);
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + LocaleManager.getMessage("goals.title", locale));
        lines.add(ChatColor.YELLOW + LocaleManager.getMessage("goals.section.daily", locale));
        lines.add(line(locale, "goals.daily.play_match", view.dailyMatches(), 1, GoalRules.DAILY_MATCH_COINS));
        lines.add(line(locale, "goals.daily.first_win", view.dailyWins(), 1, GoalRules.DAILY_WIN_COINS));
        lines.add(ChatColor.AQUA + LocaleManager.getMessage("goals.section.weekly", locale));
        lines.add(line(locale, GoalRules.WEEKLY_MATCHES.labelKey(), view.weeklyMatches(),
                GoalRules.WEEKLY_MATCHES_TARGET, GoalRules.WEEKLY_MATCHES.coins()));
        lines.add(line(locale, GoalRules.WEEKLY_WIN.labelKey(), view.weeklyWins(),
                GoalRules.WEEKLY_WINS_TARGET, GoalRules.WEEKLY_WIN.coins()));
        lines.add(line(locale, GoalRules.WEEKLY_FINISHED.labelKey(), view.weeklyFinished(),
                GoalRules.WEEKLY_FINISHED_TARGET, GoalRules.WEEKLY_FINISHED.coins()));
        lines.add(ChatColor.LIGHT_PURPLE + LocaleManager.getMessage("goals.achievements", locale,
                view.achievements(), GoalRules.CATALOG.size()));
        lines.add(ChatColor.GRAY + LocaleManager.getMessage("goals.reset", locale));
        return List.copyOf(lines);
    }

    public synchronized GoalView view(UUID playerId) {
        Progress progress = progress(playerId);
        resetWeekIfNeeded(progress);
        resetDayIfNeeded(progress);
        return new GoalView(Math.min(progress.dailyMatches, 1), Math.min(progress.dailyWins, 1),
                Math.min(progress.matches, GoalRules.WEEKLY_MATCHES_TARGET),
                Math.min(progress.wins, GoalRules.WEEKLY_WINS_TARGET),
                Math.min(progress.matches, GoalRules.WEEKLY_FINISHED_TARGET),
                progress.achievements.size());
    }

    public synchronized void shutdown() {
        saveIfDirty();
    }

    /**
     * Persists the local fallback only after the authoritative player row has
     * been committed. Startup cannot safely import goals.yml first because
     * player_goal_progress owns a foreign key to playerdata.
     */
    public synchronized void syncPlayer(UUID playerId) {
        if (playerId == null) return;
        persistAsync(playerId, progress(playerId));
    }

    private static String line(Locale locale, String labelKey, int value, int target, int coins) {
        boolean done = value >= target;
        return (done ? ChatColor.GREEN + "✔ " : ChatColor.GRAY + "• ") + ChatColor.WHITE
                + LocaleManager.getMessage(labelKey, locale) + ChatColor.GRAY + " " + value + "/" + target
                + ChatColor.GOLD + " " + LocaleManager.getMessage("goals.reward_coins", locale, coins);
    }

    private void addAchievement(Progress progress, Map<String, Reward> rewards, GoalRules.Achievement achievement,
            String game) {
        progress.achievements.add(achievement.key());
        rewards.put("achievement:" + achievement.key(), new Reward(achievement.coins(), 0, game,
                achievement.labelKey()));
    }

    /**
     * Ten matches and five modes are lifetime milestones read from match
     * history. Game modes persist the match row around the reward, so the
     * check runs a few seconds later and the next match catches any lag.
     */
    private void scheduleLifetimeMilestones(UUID playerId) {
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            PostgresGoalProgressRepository.MatchHistory history;
            try {
                history = repository.matchHistory(playerId);
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Could not read match history for goal milestones: "
                        + error.getMessage());
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> grantLifetime(playerId, history));
        }, 100L);
    }

    private synchronized void grantLifetime(UUID playerId, PostgresGoalProgressRepository.MatchHistory history) {
        Progress progress = progress(playerId);
        List<GoalRules.Achievement> earned = GoalRules.lifetimeAchievements(history.matches(),
                history.distinctModes(), progress.achievements);
        if (earned.isEmpty()) return;
        Map<String, Reward> rewards = new LinkedHashMap<>();
        for (GoalRules.Achievement achievement : earned) {
            progress.achievements.add(achievement.key());
            rewards.put("achievement:" + achievement.key(), new Reward(achievement.coins(), 0, null,
                    achievement.labelKey()));
        }
        dirty = true;
        persistAsync(playerId, progress);
        queueRewards(playerId, rewards);
    }

    private void queueRewards(UUID playerId, Map<String, Reward> rewards) {
        if (rewards.isEmpty()) {
            return;
        }
        Map<String, Reward> snapshot = Map.copyOf(rewards);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            MinigameProgressionService service = new MinigameProgressionService(null);
            Set<String> claimed = new LinkedHashSet<>();
            snapshot.forEach((key, reward) -> {
                boolean applied = reward.game() == null
                        || MinigameProgressionService.supportedMinigameKey(reward.game()) == null
                        ? service.claimGlobalReward(playerId, key, reward.coins())
                        : service.claimGoalReward(playerId, reward.game(), reward.experience(), reward.coins(), key);
                if (applied) claimed.add(key);
            });
            for (String key : claimed) {
                FunnelTelemetry.record(playerId, FunnelTelemetry.Event.REWARD_CLAIMED,
                        "source=" + key + " coins=" + snapshot.get(key).coins());
            }
            com.cookiebuild.cookiedough.cosmetics.CosmeticActivation cosmetics = plugin.getCosmeticActivation();
            if (cosmetics != null) cosmetics.onRewardsClaimed(playerId, claimed);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player online = plugin.getServer().getPlayer(playerId);
                if (online == null) {
                    return;
                }
                for (String key : claimed) {
                    Reward reward = snapshot.get(key);
                    String label = LocaleManager.getMessage(reward.labelKey(), online.locale());
                    String prefix = key.startsWith("achievement:") ? "goals.reward.achievement"
                            : key.startsWith("weekly") ? "goals.reward.weekly" : "goals.reward.daily";
                    online.sendMessage(ChatColor.GREEN + LocaleManager.getMessage(prefix, online.locale(), label)
                            + " " + ChatColor.GOLD + LocaleManager.getMessage("goals.reward_coins", online.locale(),
                                    reward.coins())
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
        LocalDate today = parisToday();
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
                progress.day = LocalDate.parse(yaml.getString(uuidText + ".day", parisToday().toString()));
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
        return ParisCalendar.weekKey(parisToday());
    }

    private static LocalDate parisToday() {
        return ParisCalendar.today();
    }

    private void loadDatabaseAndMergeYaml() {
        try {
            Map<UUID, GoalProgressSnapshot> database = repository.loadAll();
            database.forEach((playerId, snapshot) -> progressByPlayer.put(playerId, fromSnapshot(snapshot)));
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
