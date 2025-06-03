package com.cookiebuild.cookiedough.lobby;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.model.GameStats;
import com.cookiebuild.cookiedough.service.PlayerStatsService;

public class LobbyScoreboard {

    private final Player player;
    private final PlayerStatsService playerStatsService;
    private final Scoreboard scoreboard;
    private Objective objective;
    private final String websiteUrl = "www.cookie-build.com";
    private static final String SCOREBOARD_TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "Cookie Build"
            + ChatColor.RESET;

    public LobbyScoreboard(Player player, PlayerStatsService playerStatsService) {
        this.player = player;
        this.playerStatsService = playerStatsService;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            // Log an error instead of throwing, to prevent disabling plugin part if SB
            // manager is temp. unavailable
            CookieDough.getInstance().getLogger()
                    .severe("ScoreboardManager is null, cannot create scoreboard for " + player.getName());
            this.scoreboard = null; // Ensure scoreboard is null so other methods don't try to use it.
            return;
        }
        this.scoreboard = manager.getNewScoreboard();
        setupScoreboard();
    }

    private void setupScoreboard() {
        if (this.scoreboard == null)
            return;
        objective = scoreboard.getObjective("lobbyStats");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("lobbyStats", "dummy", SCOREBOARD_TITLE);
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        update(); // Initial population
    }

    public void update() {
        if (this.scoreboard == null || objective == null || !player.isOnline()) {
            return; // Don't update if scoreboard isn't setup or player offline
        }

        // Clear old scores using the current teams/entries to avoid issues
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }
        // also unregister teams to be safe, new teams will be created by setScore
        for (Team team : scoreboard.getTeams()) {
            team.unregister();
        }

        // --- Overall Stats --- //
        List<GameStats> allGameStats = playerStatsService.getPlayerStats(player.getUniqueId());
        int totalGamesPlayedAll = 0;
        int totalWinsAll = 0;
        int totalKillsAll = 0;
        int totalDeathsAll = 0;

        for (GameStats gs : allGameStats) {
            totalGamesPlayedAll += gs.getGamesPlayed();
            totalWinsAll += gs.getGamesWon();
            totalKillsAll += gs.getTotalKills();
            totalDeathsAll += gs.getTotalDeaths();
        }
        double kdrOverall = (totalDeathsAll == 0) ? totalKillsAll : (double) totalKillsAll / totalDeathsAll;
        // double winRateOverall = (totalGamesPlayedAll == 0) ? 0 : (double)
        // totalWinsAll / totalGamesPlayedAll * 100;

        // --- Scoreboard Line Definitions --- //
        int line = 15; // Start from top

        setScore(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "                 ", line--); // Top separator
        setScore(ChatColor.YELLOW + "" + ChatColor.BOLD + "GLOBAL STATS", line--);
        setScore("  " + ChatColor.GOLD + "Total Wins: " + ChatColor.WHITE + totalWinsAll, line--);
        setScore("  " + ChatColor.GOLD + "K/D Ratio: " + ChatColor.WHITE + String.format("%.2f", kdrOverall), line--);
        setScore(ChatColor.WHITE + " ", line--); // consume an extra line for spacing

        // --- MicroBattles Stats --- //
        Optional<GameStats> mbStatsOpt = playerStatsService.getPlayerStatsByGameType(player.getUniqueId(),
                "MicroBattles");
        setScore(ChatColor.AQUA + "" + ChatColor.BOLD + "MICROBATTLES", line--);
        if (mbStatsOpt.isPresent()) {
            GameStats mbStats = mbStatsOpt.get();
            Map<String, String> specificMbStats = mbStats.getFormattedSpecificStats();
            setScore("  " + ChatColor.DARK_AQUA + "Wins: " + ChatColor.WHITE + mbStats.getGamesWon() + ChatColor.GRAY
                    + " (" + mbStats.getGamesPlayed() + " P)", line--);
            setScore("  " + ChatColor.DARK_AQUA + "Players Elim: " + ChatColor.WHITE
                    + specificMbStats.getOrDefault("Players Elim.", "0"), line--);
        } else {
            setScore("  " + ChatColor.GRAY + "Play a game!", line--);
            setScore(ChatColor.WHITE + " ", line--); // consume an extra line for spacing
        }

        // --- Pitchout Stats --- //
        Optional<GameStats> poStatsOpt = playerStatsService.getPlayerStatsByGameType(player.getUniqueId(), "Pitchout");
        setScore(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "PITCHOUT", line--);
        if (poStatsOpt.isPresent()) {
            GameStats poStats = poStatsOpt.get();
            Map<String, String> specificPoStats = poStats.getFormattedSpecificStats();
            setScore("  " + ChatColor.DARK_PURPLE + "Wins: " + ChatColor.WHITE + poStats.getGamesWon() + ChatColor.GRAY
                    + " (" + poStats.getGamesPlayed() + " P)", line--);
            setScore("  " + ChatColor.DARK_PURPLE + "Players Elim: " + ChatColor.WHITE
                    + specificPoStats.getOrDefault("Players Elim.", "0"), line--);
        } else {
            setScore("  " + ChatColor.GRAY + "Play a game!", line--);
            setScore(ChatColor.WHITE + " ", line--); // consume an extra line for spacing
        }

        // Ensure website URL is always displayed
        setScore(ChatColor.GRAY + "" + ChatColor.ITALIC + websiteUrl, line--);
        setScore(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "                 ", line--); // Bottom separator

        // Ensure player has this scoreboard instance set
        if (player.getScoreboard() != this.scoreboard) {
            player.setScoreboard(this.scoreboard);
        }
    }

    private void setScore(String text, int scorePosition) {
        if (this.scoreboard == null || objective == null || scorePosition < 0)
            return;

        String entryKey = getEntryForScore(scorePosition); // Use a unique invisible entry for each line number

        Team team = scoreboard.getTeam("line" + scorePosition);
        if (team == null) {
            team = scoreboard.registerNewTeam("line" + scorePosition);
        }

        // Old way of adding entry might cause issues if entryKey was already part of
        // another team or scoreboard.
        // Ensure the entry is only on this team.
        if (!team.hasEntry(entryKey)) {
            team.addEntry(entryKey); // Add the unique, invisible entry to the team
        }

        // Split text for prefix/suffix if too long for one line (Minecraft limit is 40
        // for prefix/suffix)
        // However, modern clients usually handle longer team prefixes for sidebar
        // directly via setPrefix.
        // Bukkit itself has a limit of 16 chars for entry names if they were visible,
        // but ours are invisible.
        // Max length for a line on scoreboard is typically handled by the client, but
        // prefixes are safer.
        if (text.length() > 40) {
            team.setPrefix(text.substring(0, 40));
            // team.setSuffix(text.substring(40)); // Suffix if needed, but usually prefix
            // is enough for sidebars
        } else {
            team.setPrefix(text);
            // team.setSuffix(""); // Clear suffix if not used
        }

        objective.getScore(entryKey).setScore(scorePosition); // Set the score for the unique entry
    }

    // Generates a unique, invisible string for each score line to act as the entry
    private String getEntryForScore(int score) {
        return ChatColor.values()[score % ChatColor.values().length].toString() +
                ChatColor.values()[(score / ChatColor.values().length) % ChatColor.values().length].toString() +
                ChatColor.RESET;
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