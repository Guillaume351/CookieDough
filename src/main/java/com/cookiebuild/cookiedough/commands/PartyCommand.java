package com.cookiebuild.cookiedough.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.retention.PartyManager;

public final class PartyCommand implements CommandExecutor {
    private final PartyManager parties;

    public PartyCommand(PartyManager parties) {
        this.parties = parties;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        String result;
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            result = parties.describe(player);
        } else if (args[0].equalsIgnoreCase("create")) {
            result = parties.create(player) ? "Party created." : "You are already in a party.";
        } else if (args[0].equalsIgnoreCase("leave")) {
            result = parties.leave(player);
        } else if ((args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("join")) && args.length > 1) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                result = "That player is not online.";
            } else {
                result = args[0].equalsIgnoreCase("invite") ? parties.invite(player, target) : parties.join(player, target);
            }
        } else {
            result = "/party [create|invite <player>|join <leader>|leave|list]";
        }
        player.sendMessage(ChatColor.YELLOW + result);
        return true;
    }
}
