package com.cookiebuild.cookiedough.commands;

import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        LobbyManager.teleportPlayerToLobby(cookiePlayer);
        player.sendMessage(LocaleManager.getMessage("lobby.teleported", player.locale(), player.getName()));
        return true;
    }
}