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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class LobbyScoreboard {
    private record GameStatsSpec(String gameType, String progressionKey, String label,
            NamedTextColor headerColor, NamedTextColor statsColor) {
    }

    private static final List<GameStatsSpec> GAME_STATS = List.of(
            new GameStatsSpec("MicroBattles", MinigameProgressionService.MICROBATTLES, "MICRO",
                    NamedTextColor.AQUA, NamedTextColor.DARK_AQUA),
            new GameStatsSpec("Pitchout", MinigameProgressionService.PITCHOUT, "PITCH",
                    NamedTextColor.LIGHT_PURPLE, NamedTextColor.DARK_PURPLE),
            new GameStatsSpec("SkyWars", MinigameProgressionService.SKYWARS, "SKY",
                    NamedTextColor.GOLD, NamedTextColor.YELLOW));
    private final Player player;
    private final Scoreboard scoreboard;
    private Objective objective;
    private final String websiteUrl = "www.cookie-build.com";
    private static final Component SCOREBOARD_TITLE = Component.text("Cookie Build")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD);
    private static final Gson GSON = new Gson();

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

        PlayerStats(List<PlayerMatchPerformance> performances, Long playTime, int coins,
                Map<String, PlayerStatsService.ProgressionSnapshot> progressionByGame) {
            this.allPerformances = performances;
            this.totalPlayTime = playTime;
            this.coins = coins;
            this.progressionByGame = Map.copyOf(progressionByGame);
        }
    }

    public LobbyScoreboard(Player player) {
        this.player = player;

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
        return cachedStats != null ? cachedStats : new PlayerStats(new ArrayList<>(), 0L, 0, Map.of());
    }

    private void refreshStatsAsync(java.util.UUID playerId) {
        if (!refreshInFlight.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
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
                statsCache.put(playerId, newStats);
                lastCacheUpdate.put(playerId, System.currentTimeMillis());
                Bukkit.getScheduler().runTask(CookieDough.getInstance(), this::update);
            } catch (Exception e) {
                CookieDough.getInstance().getLogger().warning(
                        "Could not refresh lobby stats for " + player.getName() + ": " + e.getMessage());
            } finally {
                refreshInFlight.set(false);
            }
        });
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

        int line = 15;

        // Decorative separator
        setScore(Component.text("                 ").color(NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.STRIKETHROUGH), line--);

        // Global stats
        setScore(Component.text("PROGRESS").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD), line--);
        setScore(Component.text("  Coins: ").color(NamedTextColor.GOLD)
                .append(Component.text(stats.coins).color(NamedTextColor.WHITE))
                .append(Component.text(" • " + allPerformances.size() + " games").color(NamedTextColor.GRAY)), line--);

        // Play Time - use cached data
        Long pastSessionsPlayTime = stats.totalPlayTime;

        // Calculate current session's live play time
        long currentSessionLivePlayTime = 0;
        Date currentSessionStartTime = PlayerWrapperListener.getPlayerLoginTime(player.getUniqueId());
        if (currentSessionStartTime != null) {
            currentSessionLivePlayTime = new Date().getTime() - currentSessionStartTime.getTime();
        }
        long totalPlayTime = pastSessionsPlayTime + currentSessionLivePlayTime;

        setScore(Component.text("  Play: ").color(NamedTextColor.GOLD)
                .append(Component.text(formatPlayTime(totalPlayTime)).color(NamedTextColor.WHITE)), line--);
        setScore(Component.text(" "), line--);

        for (GameStatsSpec game : GAME_STATS) {
            PlayerStatsService.ProgressionSnapshot progression = stats.progressionByGame.getOrDefault(
                    game.progressionKey(), PlayerStatsService.ProgressionSnapshot.empty());
            setScore(Component.text(game.label() + " L" + progression.level() + " "
                            + progression.experience() + "/" + progression.nextLevelExperience())
                    .color(game.headerColor()).decorate(TextDecoration.BOLD), line--);
            List<PlayerMatchPerformance> performances = performancesByGame.getOrDefault(game.gameType(), List.of());
            if (performances.isEmpty()) {
                setScore(Component.text("  Play a game!").color(NamedTextColor.GRAY), line--);
            } else {
                displayGameStats(game, performances, line--);
            }
        }

        // Website and separator
        setScore(Component.text(" "), line--);
        setScore(Component.text(websiteUrl).color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC), line--);
        setScore(Component.text("                 ").color(NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.STRIKETHROUGH), line--);

        if (player.getScoreboard() != this.scoreboard) {
            player.setScoreboard(this.scoreboard);
        }
    }

    private void displayGameStats(GameStatsSpec game, List<PlayerMatchPerformance> performances, int line) {
        int wins = (int) performances.stream()
                .filter(p -> p.getMatch().getWinners().stream()
                        .anyMatch(winner -> winner.getId().equals(player.getUniqueId())))
                .count();

        int eliminations = performances.stream().mapToInt(PlayerMatchPerformance::getKillsInMatch).sum();
        setScore(Component.text("  W ").color(game.statsColor())
                .append(Component.text(wins).color(NamedTextColor.WHITE))
                .append(Component.text(" • K ").color(game.statsColor()))
                .append(Component.text(eliminations).color(NamedTextColor.WHITE))
                .append(Component.text(" • P " + performances.size()).color(NamedTextColor.GRAY)), line);
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

    private String getEntryForScore(int score) {
        // Use section symbol (§) to create invisible unique identifiers
        return "§" + (score % 10) + "§" + ((score / 10) % 10);
    }

    private String getMetricFromJson(String json, String key) {
        try {
            Map<String, String> metrics = GSON.fromJson(json,
                    new TypeToken<Map<String, String>>() {
                    }.getType());
            return metrics.getOrDefault(key, "0");
        } catch (Exception e) {
            return "0";
        }
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
