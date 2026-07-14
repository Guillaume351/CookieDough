package com.cookiebuild.cookiedough.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.game.GameManager;

public final class QuickPlayCommand implements CommandExecutor {
    private final LobbyManager lobbyManager;

    public QuickPlayCommand(LobbyManager lobbyManager) {
        this.lobbyManager = lobbyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("replay")) {
            FunnelTelemetry.record(player, FunnelTelemetry.Event.REMATCH_CLICKED, "");
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
            if (cookiePlayer != null && (cookiePlayer.getState() != PlayerState.LOBBY
                    || GameManager.getGameOfPlayer(cookiePlayer) != null)) {
                LobbyManager.teleportPlayerToLobby(cookiePlayer);
            }
        }
        if (args.length > 0 && !args[0].equalsIgnoreCase("replay")) {
            if (!CookieDough.getInstance().getPartyManager().isAvailable()) {
                player.sendMessage(org.bukkit.ChatColor.YELLOW
                        + "Party service is temporarily unavailable. Please try again.");
                return true;
            }
            if (CookieDough.getInstance().getPartyManager().getPartyId(player.getUniqueId()) != null) {
                player.sendMessage(org.bukkit.ChatColor.YELLOW
                        + "Direct game selection is currently solo-only. Party Quick Play will choose a compatible game.");
                return true;
            }
            lobbyManager.requestGame(player, args[0]);
            return true;
        }
        String partyResult = CookieDough.getInstance().getPartyManager().queueParty(player);
        if (partyResult != null) {
            if (!partyResult.isBlank()) {
                player.sendMessage(org.bukkit.ChatColor.YELLOW + partyResult);
            }
            return true;
        }
        lobbyManager.requestQuickPlay(player);
        return true;
    }
}
