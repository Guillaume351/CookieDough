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
        if (args.length == 2 && args[0].equalsIgnoreCase("notice")) {
            try {
                CookieDough.getInstance().getRallyManager().acceptInGameNotice(
                        player, java.util.UUID.fromString(args[1]));
            } catch (IllegalArgumentException invalidToken) {
                player.sendMessage(org.bukkit.ChatColor.YELLOW + com.cookiebuild.cookiedough.utils.LocaleManager
                        .getMessage("rally.invite.expired", player.locale()));
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            boolean cancelled = GameManager.cancelQueueIntent(player.getUniqueId());
            player.sendMessage(org.bukkit.ChatColor.YELLOW + com.cookiebuild.cookiedough.utils.LocaleManager
                    .getMessage(cancelled ? "lobby.queue.intent_cancelled" : "lobby.queue.intent_none",
                            player.locale()));
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
            lobbyManager.requestSelectedActivity(player, args[0]);
            return true;
        }
        lobbyManager.requestSelectedQuickPlay(player);
        return true;
    }
}
