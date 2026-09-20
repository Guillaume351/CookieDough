package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class LobbyScoreboard {
    private record GameStatsSpec(String gameType, String progressionKey, String label,
            NamedTextColor headerColor) {
    }

    private static final List<GameStatsSpec> GAME_STATS = List.of(
            new GameStatsSpec("MicroBattles", MinigameProgressionService.MICROBATTLES, "MICRO",
                    NamedTextColor.AQUA),
            new GameStatsSpec("Pitchout", MinigameProgressionService.PITCHOUT, "PITCH",
                    NamedTextColor.LIGHT_PURPLE),
            new GameStatsSpec("SkyWars", MinigameProgressionService.SKYWARS, "SKY",
                    NamedTextColor.GOLD),
            new GameStatsSpec("BuildBattles", MinigameProgressionService.BUILDBATTLES, "BUILD",
                    NamedTextColor.GREEN),
            new GameStatsSpec("TurfWars", MinigameProgressionService.TURFWARS, "TURF",
                    NamedTextColor.RED),
            new GameStatsSpec("BedWars", MinigameProgressionService.BEDWARS, "BED",
                    NamedTextColor.YELLOW));
    private final Player player;
    private final PluginTaskDispatcher tasks;
    private final Scoreboard scoreboard;
    private Objective objective;
    private final String websiteUrl = "www.cookie-build.com";
    private static final Component SCOREBOARD_TITLE = Component.text("Cookie Build")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD);

    // Cache system
    private static final Map<java.util.UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();
    private static final Map<java.util.UUID, Long> lastCacheUpdate = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 60000; // 1 minute cache

    // Task tracking
    private BukkitTask updateTask;
    private long lastScoreboardUpdate = 0;
    private static final long SCOREBOARD_UPDATE_INTERVAL = 10000; // Update every 10 seconds instead of 5
    private final AtomicBoolean refreshInFlight = new AtomicBoolean();

    // Cached stats data structure
    private static class PlayerStats {
        List<PlayerMatchPerformance> allPerformances;
        Long totalPlayTime;
        int coins;
        Map<String, PlayerStatsService.ProgressionSnapshot> progressionByGame;
        boolean loaded;

        PlayerStats(List<PlayerMatchPerformance> performances, Long playTime, int coins,
                Map<String, PlayerStatsService.ProgressionSnapshot> progressionByGame) {
            this(performances, playTime, coins, progressionByGame, true);
        }

        PlayerStats(List<PlayerMatchPerformance> performances, Long playTime, int coins,
                Map<String, PlayerStatsService.ProgressionSnapshot> progressionByGame, boolean loaded) {
            this.allPerformances = performances;
            this.totalPlayTime = playTime;
            this.coins = coins;
            this.progressionByGame = Map.copyOf(progressionByGame);
            this.loaded = loaded;
        }
    }

    public LobbyScoreboard(Player player) {
        this.player = player;
        this.tasks = new PluginTaskDispatcher(CookieDough.getInstance());

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            CookieDough.getInstance().getLogger()
                    .severe("ScoreboardManager is null, cannot create scoreboard for " + player.getName());
            this.scoreboard = null;
            return;
        }
        this.scoreboard = manager.getNewScoreboard();
        setupScoreboard();

        // Schedule less frequent updates - every 10 seconds instead of 5
        this.updateTask = Bukkit.getScheduler().runTaskTimer(CookieDough.getInstance(), () -> {
            if (this.scoreboard != null && this.objective != null && player.isOnline()) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastScoreboardUpdate >= SCOREBOARD_UPDATE_INTERVAL) {
                    update();
                    lastScoreboardUpdate = currentTime;
                }
            }
        }, 100L, 200L); // Check every 10 ticks (0.5 seconds) but only update every 10 seconds
    }

    private void setupScoreboard() {
        if (this.scoreboard == null)
            return;
        objective = scoreboard.getObjective("lobbyStats");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("lobbyStats", Criteria.DUMMY, SCOREBOARD_TITLE);
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        update();
    }

    private PlayerStats getCachedStats() {
        java.util.UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        PlayerStats cachedStats = statsCache.get(playerId);
        Long lastUpdate = lastCacheUpdate.get(playerId);
        if (cachedStats != null && lastUpdate != null &&
                (currentTime - lastUpdate) < CACHE_DURATION) {
            return cachedStats;
        }
        refreshStatsAsync(playerId);
        return cachedStats != null ? cachedStats : new PlayerStats(new ArrayList<>(), 0L, 0, Map.of(), false);
    }

    private void refreshStatsAsync(java.util.UUID playerId) {
        if (!refreshInFlight.compareAndSet(false, true)) {
            return;
        }
        boolean scheduled = tasks.runAsync(() -> {
            try {
                List<PlayerMatchPerformance> allPerformances = PlayerStatsService
                        .getPlayerPerformancesStatic(playerId);
                Long totalPlayTime = PlayerStatsService.getTotalPlayTimeStatic(playerId);
                int coins = PlayerStatsService.getCoinsStatic(playerId);
                Map<String, PlayerStatsService.ProgressionSnapshot> progressionByGame = new LinkedHashMap<>();
                for (GameStatsSpec game : GAME_STATS) {
                    progressionByGame.put(game.progressionKey(), PlayerStatsService.getProgressionStatic(
                            playerId, game.progressionKey()));
                }
                PlayerStats newStats = new PlayerStats(allPerformances, totalPlayTime, coins, progressionByGame);
                tasks.runSync(() -> {
                    statsCache.put(playerId, newStats);
                    lastCacheUpdate.put(playerId, System.currentTimeMillis());
                    update();
                });
            } catch (Exception e) {
                if (tasks.isActive()) {
                    CookieDough.getInstance().getLogger().warning(
                            "Could not refresh lobby stats for " + player.getName() + ": " + e.getMessage());
                }
            } finally {
                refreshInFlight.set(false);
            }
        });
        if (!scheduled) {
            refreshInFlight.set(false);
        }
    }

    public void update() {
        if (this.scoreboard == null || objective == null || !player.isOnline()) {
            return;
        }

        // Check if player is in lobby state
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null || cookiePlayer.getState() != PlayerState.LOBBY) {
            return;
        }

        // Get cached stats
        PlayerStats stats = getCachedStats();
        List<PlayerMatchPerformance> allPerformances = stats.allPerformances;

        // Group performances by game type
        Map<String, List<PlayerMatchPerformance>> performancesByGame = allPerformances.stream()
                .collect(Collectors.groupingBy(p -> p.getMatch().getGameType()));

        // A sidebar renders at most fifteen entries. Six games stay visible by
        // keeping each game to one compact progression/result line.
        int line = 14;

        if (stats.loaded && allPerformances.isEmpty()) {
            setScore(Component.text(LocaleManager.getMessage("beginner.scoreboard.title", player.locale()))
                    .color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD), line--);
            setScore(Component.text("1. " + LocaleManager.getMessage(
                    "beginner.scoreboard.menu", player.locale())).color(NamedTextColor.WHITE), line--);
            setScore(Component.text("2. " + LocaleManager.getMessage(
                    "beginner.scoreboard.queue", player.locale())).color(NamedTextColor.WHITE), line--);
            setScore(Component.text("3. " + LocaleManager.getMessage(
                    "beginner.scoreboard.practice", player.locale())).color(NamedTextColor.WHITE), line--);
            setScore(Component.text(" "), line--);
            setScore(Component.text(websiteUrl).color(NamedTextColor.GRAY)
                    .decorate(TextDecoration.ITALIC), line);
            clearScoresBelow(line - 1);
            if (player.getScoreboard() != this.scoreboard) player.setScoreboard(this.scoreboard);
            return;
        }

        // Global stats
        setScore(Component.text(LocaleManager.getMessage("scoreboard.progress", player.locale()))
                .color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD), line--);
        setScore(Component.text("  " + LocaleManager.getMessage("scoreboard.coins_games", player.locale(),
                        stats.coins, allPerformances.size())).color(NamedTextColor.WHITE), line--);

        // Play Time - use cached data
        Long pastSessionsPlayTime = stats.totalPlayTime;

        // Calculate current session's live play time
        long currentSessionLivePlayTime = 0;
        Date currentSessionStartTime = PlayerWrapperListener.getPlayerLoginTime(player.getUniqueId());
        if (currentSessionStartTime != null) {
            currentSessionLivePlayTime = new Date().getTime() - currentSessionStartTime.getTime();
        }
        long totalPlayTime = pastSessionsPlayTime + currentSessionLivePlayTime;

        setScore(Component.text("  " + LocaleManager.getMessage("scoreboard.play", player.locale(),
                        formatPlayTime(totalPlayTime))).color(NamedTextColor.WHITE), line--);
        setScore(Component.text(" "), line--);

        for (GameStatsSpec game : GAME_STATS) {
            PlayerStatsService.ProgressionSnapshot progression = stats.progressionByGame.getOrDefault(
                    game.progressionKey(), PlayerStatsService.ProgressionSnapshot.empty());
            List<PlayerMatchPerformance> performances = performancesByGame.getOrDefault(game.gameType(), List.of());
            int wins = (int) performances.stream().filter(p -> p.getMatch().getWinners().stream()
                    .anyMatch(winner -> winner.getId().equals(player.getUniqueId()))).count();
            setScore(Component.text(game.label() + " L" + progression.level())
                    .color(game.headerColor()).decorate(TextDecoration.BOLD)
                    .append(Component.text("  " + (performances.isEmpty()
                            ? LocaleManager.getMessage("scoreboard.game_empty", player.locale())
                            : LocaleManager.getMessage("scoreboard.game_record", player.locale(),
                                    wins, performances.size())))
                            .color(performances.isEmpty() ? NamedTextColor.GRAY : NamedTextColor.WHITE)), line--);
        }

        // Website
        setScore(Component.text(websiteUrl).color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC), line--);
        clearScoresBelow(line);

        if (player.getScoreboard() != this.scoreboard) {
            player.setScoreboard(this.scoreboard);
        }
    }

    private void setScore(Component text, int score) {
        if (this.scoreboard == null || objective == null || score < 0)
            return;

        String entry = getEntryForScore(score);
        Team team = scoreboard.getTeam("line" + score);
        if (team == null) {
            team = scoreboard.registerNewTeam("line" + score);
        }

        // Only add entry if not already present to prevent blinking
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }

        // Only update prefix if it's different to prevent blinking
        if (!team.prefix().equals(text)) {
            team.prefix(text);
        }

        objective.getScore(entry).setScore(score);
    }

    private void clearScoresBelow(int highestUnusedScore) {
        for (int score : unusedScores(highestUnusedScore)) {
            String entry = getEntryForScore(score);
            scoreboard.resetScores(entry);
            Team team = scoreboard.getTeam("line" + score);
            if (team != null) team.unregister();
        }
    }

    static List<Integer> unusedScores(int highestUnusedScore) {
        if (highestUnusedScore < 0) return List.of();
        List<Integer> scores = new ArrayList<>(highestUnusedScore + 1);
        for (int score = highestUnusedScore; score >= 0; score--) scores.add(score);
        return List.copyOf(scores);
    }

    private String getEntryForScore(int score) {
        // Use section symbol (§) to create invisible unique identifiers
        return "§" + (score % 10) + "§" + ((score / 10) % 10);
    }

    private String formatPlayTime(Long milliseconds) {
        if (milliseconds == null || milliseconds <= 0) {
            return "00:00";
        }

        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        return String.format("%02d:%02d", hours, minutes);
    }

    public void show() {
        if (this.scoreboard == null || !player.isOnline())
            return;
        player.setScoreboard(scoreboard);
        update(); // Refresh content on show
    }

    public void hide() {
        if (this.scoreboard == null || !player.isOnline() || player.getScoreboard() != this.scoreboard)
            return;
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    public void cleanup() {
        tasks.close();
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        // Clean up cache for this player
        java.util.UUID playerId = player.getUniqueId();
        statsCache.remove(playerId);
        lastCacheUpdate.remove(playerId);

        if (scoreboard != null) {
            // Unregister all teams
            for (Team team : scoreboard.getTeams()) {
                team.unregister();
            }
            // Unregister objective
            if (objective != null) {
                objective.unregister();
            }
        }
    }

    // Method to invalidate cache when player completes a game
    public static void invalidatePlayerCache(java.util.UUID playerId) {
        statsCache.remove(playerId);
        lastCacheUpdate.remove(playerId);
    }
}
