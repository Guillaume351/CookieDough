package com.cookiebuild.cookiedough.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.retention.RallyManager;

public final class RallyCommand implements CommandExecutor {
    private final RallyManager rallies;

    public RallyCommand(RallyManager rallies) {
        this.rallies = rallies;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length > 1) {
            player.sendMessage(ChatColor.YELLOW + "/rally [gamemode]");
            return true;
        }
        String gamemode = args.length == 0 ? "" : args[0];
        rallies.request(player, gamemode, result -> {
            if (player.isOnline() && !result.isBlank()) {
                player.sendMessage(ChatColor.YELLOW + result);
            }
        });
        return true;
    }
}
