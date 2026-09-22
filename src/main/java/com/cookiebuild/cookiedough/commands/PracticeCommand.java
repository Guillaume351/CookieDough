package com.cookiebuild.cookiedough.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.retention.PracticeManager;

public final class PracticeCommand implements CommandExecutor {
    private final PracticeManager practice;

    public PracticeCommand(PracticeManager practice) {
        this.practice = practice;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        practice.toggle(player);
        return true;
    }
}
