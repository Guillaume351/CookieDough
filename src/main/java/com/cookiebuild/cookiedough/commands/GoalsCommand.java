package com.cookiebuild.cookiedough.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.retention.PlayerGoalTracker;

public final class GoalsCommand implements CommandExecutor {
    private final PlayerGoalTracker goals;

    public GoalsCommand(PlayerGoalTracker goals) {
        this.goals = goals;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        player.sendMessage(ChatColor.GOLD + goals.summary(player.getUniqueId()));
        return true;
    }
}
