package com.cookiebuild.cookiedough.lobby;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class LobbyScoreboard {
    private final Player player;
    private final Scoreboard scoreboard;
    private Objective objective;
    private final String websiteUrl = "www.cookie-build.com";
    private static final Component SCOREBOARD_TITLE = Component.text("Cookie Build")
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD);
    private static final Gson GSON = new Gson();

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

        // Schedule regular scoreboard updates
        Bukkit.getScheduler().runTaskTimer(CookieDough.getInstance(), () -> {
            if (this.scoreboard != null && this.objective != null && player.isOnline()) {
                update();
            }
        }, 20 * 5, 20 * 5); // Update every 5 seconds (5 * 20 ticks)
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

    public void update() {
        if (this.scoreboard == null || objective == null || !player.isOnline()) {
            return;
        }

        // Check if player is in lobby state
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null || cookiePlayer.getState() != PlayerState.LOBBY) {
            return;
        }

        // Clear old scores that are no longer needed
        for (String entry : scoreboard.getEntries()) {
            if (!entry.startsWith("§")) {
                scoreboard.resetScores(entry);
            }
        }
        // Only unregister teams that are no longer used
        for (Team team : scoreboard.getTeams()) {
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }

        // Get all match performances for the player
        List<PlayerMatchPerformance> allPerformances = PlayerStatsService
                .getPlayerPerformancesStatic(player.getUniqueId());

        // Calculate overall stats
        int totalKillsAll = allPerformances.stream().mapToInt(PlayerMatchPerformance::getKillsInMatch).sum();
        int totalDeathsAll = allPerformances.stream().mapToInt(PlayerMatchPerformance::getDeathsInMatch).sum();
        double kdrOverall = (totalDeathsAll == 0) ? totalKillsAll : (double) totalKillsAll / totalDeathsAll;

        // Group performances by game type
        Map<String, List<PlayerMatchPerformance>> performancesByGame = allPerformances.stream()
                .collect(Collectors.groupingBy(p -> p.getMatch().getGameType()));

        int line = 15;

        // Decorative separator
        setScore(Component.text("                 ").color(NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.STRIKETHROUGH), line--);

        // Global stats
        setScore(Component.text("GLOBAL STATS").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD), line--);
        setScore(Component.text("  Games played: ").color(NamedTextColor.GOLD)
                .append(Component.text(allPerformances.size()).color(NamedTextColor.WHITE)), line--);

        setScore(Component.text(" "), line--);

        // Play Time - use static method to avoid lazy loading issues
        Long pastSessionsPlayTime = PlayerStatsService.getTotalPlayTimeStatic(player.getUniqueId());

        // Calculate current session's live play time
        long currentSessionLivePlayTime = 0;
        Date currentSessionStartTime = PlayerWrapperListener.getPlayerLoginTime(player.getUniqueId());
        if (currentSessionStartTime != null) {
            currentSessionLivePlayTime = new Date().getTime() - currentSessionStartTime.getTime();
        }
        long totalPlayTime = pastSessionsPlayTime + currentSessionLivePlayTime;

        setScore(Component.text("PLAY TIME").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD), line--);
        setScore(Component.text("  ").append(Component.text(formatPlayTime(totalPlayTime)).color(NamedTextColor.WHITE)),
                line--);
        setScore(Component.text(" "), line--);

        // MicroBattles Stats
        List<PlayerMatchPerformance> mbPerformances = performancesByGame.getOrDefault("MicroBattles", List.of());
        setScore(Component.text("MICROBATTLES").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD), line--);
        if (!mbPerformances.isEmpty()) {
            displayGameStats("MicroBattles", mbPerformances, line);
            line -= 2;
        } else {
            setScore(Component.text("  Play a game!").color(NamedTextColor.GRAY), line--);
            setScore(Component.text(" "), line--);
        }

        // Pitchout Stats
        List<PlayerMatchPerformance> poPerformances = performancesByGame.getOrDefault("Pitchout", List.of());
        setScore(Component.text("PITCHOUT").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD), line--);
        if (!poPerformances.isEmpty()) {
            displayGameStats("Pitchout", poPerformances, line);
            line -= 2;
        } else {
            setScore(Component.text("  Play a game!").color(NamedTextColor.GRAY), line--);
            setScore(Component.text(" "), line--);
        }

        // Website and separator
        setScore(Component.text(websiteUrl).color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC), line--);
        setScore(Component.text("                 ").color(NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.STRIKETHROUGH), line--);

        if (player.getScoreboard() != this.scoreboard) {
            player.setScoreboard(this.scoreboard);
        }
    }

    private void displayGameStats(String gameType, List<PlayerMatchPerformance> performances, int line) {
        NamedTextColor color = gameType.equals("MicroBattles") ? NamedTextColor.DARK_AQUA : NamedTextColor.DARK_PURPLE;

        int wins = (int) performances.stream()
                .filter(p -> p.getMatch().getWinners().stream()
                        .anyMatch(winner -> winner.getId().equals(player.getUniqueId())))
                .count();

        setScore(Component.text("  Wins: ").color(color)
                .append(Component.text(wins).color(NamedTextColor.WHITE))
                .append(Component.text(" (" + performances.size() + " P)").color(NamedTextColor.GRAY)), line--);

        int eliminations = performances.stream()
                .mapToInt(p -> p.getKillsInMatch())
                .sum();
        setScore(Component.text("  Kills: ").color(color)
                .append(Component.text(eliminations).color(NamedTextColor.WHITE)), line--);
    }

    private void setScore(Component text, int score) {
        if (this.scoreboard == null || objective == null || score < 0)
            return;

        String entry = getEntryForScore(score);
        Team team = scoreboard.getTeam("line" + score);
        if (team == null) {
            team = scoreboard.registerNewTeam("line" + score);
        }
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }

        team.prefix(text);
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
        hide();
        if (this.scoreboard == null)
            return;
        // Unregister teams safely
        for (Team team : scoreboard.getTeams()) {
            try {
                team.unregister();
            } catch (IllegalStateException e) {
                /* Already unregistered */ }
        }
        if (objective != null) {
            try {
                objective.unregister();
            } catch (IllegalStateException e) {
                CookieDough.getInstance().getLogger().warning("LobbyScoreboard: Objective already unregistered for "
                        + player.getName() + ": " + e.getMessage());
            }
            objective = null;
        }
    }
}