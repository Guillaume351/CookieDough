package com.cookiebuild.cookiedough.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.retention.CommunityEventManager;

public final class EventsCommand implements CommandExecutor {
    private final CommunityEventManager events;

    public EventsCommand(CommunityEventManager events) {
        this.events = events;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("remind")) {
            boolean enabled = events.toggleReminder(player);
            player.sendMessage((enabled ? ChatColor.GREEN : ChatColor.YELLOW)
                    + "Event reminders " + (enabled ? "enabled." : "disabled."));
        } else {
            events.show(player);
        }
        return true;
    }
}
