package com.cookiebuild.cookiedough.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.retention.FriendManager;

public final class FriendCommand implements TabExecutor {
    private static final List<String> ACTIONS = List.of("list", "add", "accept", "deny", "remove");
    private final FriendManager friends;

    public FriendCommand(FriendManager friends) {
        this.friends = friends;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        java.util.function.Consumer<String> reply = result -> {
            if (player.isOnline()) player.sendMessage(ChatColor.YELLOW + result);
        };
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            friends.describe(player, reply);
            return true;
        }
        if (args.length < 2) {
            reply.accept("/friend [list|add <player>|accept <player>|deny <player>|remove <player>]");
            return true;
        }
        String targetName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> friends.request(player, targetName, reply);
            case "accept" -> friends.accept(player, targetName, reply);
            case "deny" -> friends.deny(player, targetName, reply);
            case "remove" -> friends.remove(player, targetName, reply);
            default -> reply.accept("/friend [list|add <player>|accept <player>|deny <player>|remove <player>]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return matching(ACTIONS, args[0]);
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList(), args[1]);
        }
        return List.of();
    }

    private static List<String> matching(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) matches.add(value);
        }
        return matches;
    }
}
