package com.cookiebuild.cookiedough.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;

public class CustomScoreboardManager {

    private final Map<Player, Scoreboard> playerScoreboards = new HashMap<>();

    public void createScoreboard(Player player, String title) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            Scoreboard scoreboard = manager.getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective("scoreboard", "dummy", title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            player.setScoreboard(scoreboard);
            playerScoreboards.put(player, scoreboard);
        }
    }

    public void updateScore(Player player, String entry, int score) {
        Scoreboard scoreboard = playerScoreboards.get(player);
        if (scoreboard != null) {
            Objective objective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
            if (objective != null) {
                Score scoreEntry = objective.getScore(entry);
                scoreEntry.setScore(score);
            }
        }
    }

    public void removeScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        playerScoreboards.remove(player);
    }
}