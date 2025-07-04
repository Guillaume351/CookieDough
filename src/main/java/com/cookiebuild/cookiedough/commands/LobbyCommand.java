package com.cookiebuild.cookiedough.commands;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;

public class LobbyCommand implements CommandExecutor {
    private final LobbyManager lobbyManager;

    public LobbyCommand(LobbyManager lobbyManager) {
        this.lobbyManager = lobbyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        // Check for admin subcommands
        if (args.length > 0 && player.hasPermission("cookiedough.admin")) {
            if (args[0].equalsIgnoreCase("addsign")) {
                return handleAddSign(player);
            } else if (args[0].equalsIgnoreCase("listsigns")) {
                return handleListSigns(player);
            } else if (args[0].equalsIgnoreCase("refreshsigns")) {
                return handleRefreshSigns(player);
            } else if (args[0].equalsIgnoreCase("debugsigns")) {
                return handleDebugSigns(player);
            } else if (args[0].equalsIgnoreCase("help")) {
                return handleHelp(player);
            }
        }

        // Default behavior - teleport to lobby
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        LobbyManager.teleportPlayerToLobby(cookiePlayer);
        player.sendMessage(LocaleManager.getMessage("lobby.teleported", player.locale(), player.getName()));
        return true;
    }

    private boolean handleAddSign(Player player) {
        Block targetBlock = player.getTargetBlockExact(10);
        if (targetBlock == null || !(targetBlock.getState() instanceof Sign)) {
            player.sendMessage(ChatColor.RED + "You must be looking at a sign!");
            return true;
        }

        Sign sign = (Sign) targetBlock.getState();
        lobbyManager.addGameSign(sign);
        player.sendMessage(ChatColor.GREEN + "Sign added to the game sign system!");
        player.sendMessage(ChatColor.YELLOW + "Location: " + sign.getBlock().getLocation());
        return true;
    }

    private boolean handleListSigns(Player player) {
        int signCount = lobbyManager.getGameSigns().size();
        player.sendMessage(ChatColor.GOLD + "Total registered game signs: " + signCount);

        for (int i = 0; i < lobbyManager.getGameSigns().size(); i++) {
            Sign sign = lobbyManager.getGameSigns().get(i);
            player.sendMessage(ChatColor.YELLOW + "Sign " + i + ": " + sign.getBlock().getLocation());
        }
        return true;
    }

    private boolean handleRefreshSigns(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Forcing sign refresh for all players...");
        lobbyManager.forceSignRefreshForAllPlayers();
        player.sendMessage(ChatColor.GREEN + "Sign refresh completed for all players!");
        return true;
    }

    private boolean handleDebugSigns(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Running comprehensive sign debug refresh...");
        player.sendMessage(ChatColor.GRAY + "Check console for detailed debug output.");
        lobbyManager.debugRefreshAllSigns();
        player.sendMessage(ChatColor.GREEN + "Debug refresh completed! Check console for details.");
        return true;
    }

    private boolean handleHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Lobby Admin Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/lobby addsign" + ChatColor.WHITE
                + " - Add the sign you're looking at to the game system");
        player.sendMessage(
                ChatColor.YELLOW + "/lobby listsigns" + ChatColor.WHITE + " - List all registered game signs");
        player.sendMessage(ChatColor.YELLOW + "/lobby refreshsigns" + ChatColor.WHITE
                + " - Force refresh all signs for all players");
        player.sendMessage(ChatColor.YELLOW + "/lobby debugsigns" + ChatColor.WHITE
                + " - Run comprehensive sign debug refresh (check console)");
        player.sendMessage(ChatColor.YELLOW + "/lobby help" + ChatColor.WHITE + " - Show this help");
        player.sendMessage(ChatColor.YELLOW + "/lobby" + ChatColor.WHITE + " - Teleport to lobby");
        return true;
    }
}