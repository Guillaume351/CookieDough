package com.cookiebuild.cookiedough.listener;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.dao.GenericDAOImpl;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.lobby.LobbyScoreboard;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.cookiedough.utils.DiscordUtils;
import com.cookiebuild.cookiedough.utils.LocaleManager;

public class PlayerWrapperListener implements Listener {

    private final Map<UUID, LobbyScoreboard> playerLobbyScoreboards = new HashMap<>();
    private final PlayerStatsService playerStatsService;

    public PlayerWrapperListener() {
        this.playerStatsService = CookieDough.getPlayerStatsService();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);

        Player player = event.getPlayer();

        for (Player p : event.getPlayer().getServer().getOnlinePlayers()) {
            p.sendMessage(
                    ChatColor.GREEN + LocaleManager.getMessage("player.joined.server", p.locale(), player.getName()));
        }

        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null) {
            cookiePlayer = new CookiePlayer(player);
        }

        LobbyManager.teleportPlayerToLobby(cookiePlayer);

        player.sendMessage(
                ChatColor.GREEN + LocaleManager.getMessage("welcome.message", player.locale(), player.getName()));

        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&',
                        LocaleManager.getMessage("welcome.message", player.locale(), player.getName())),
                "",
                10, 60, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

        Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
            GenericDAOImpl<PlayerData> playerDataDAO = new GenericDAOImpl<>(PlayerData.class);
            PlayerData playerData = playerDataDAO.findById(player.getUniqueId());
            if (playerData == null) {
                playerData = new PlayerData();
                playerData.setId(player.getUniqueId());
                playerData.setName(player.getName());
                playerData.setCreatedAt(new Date());
                playerData.setLastLogin(new Date());
                playerDataDAO.save(playerData);
                CookieDough.getInstance().getLogger().info("Player " + player.getName() + " created");

                String webhookUrl = System.getenv("DISCORD_NEW_PLAYER_WEBHOOK_URL");

                DiscordUtils.sendDiscordMessage(webhookUrl,
                        "A new player, " + player.getName() + ", has joined the server!");
            } else {
                playerData.setLastLogin(new Date());
                playerDataDAO.update(playerData);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        Player player = event.getPlayer();
        LobbyScoreboard scoreboard = playerLobbyScoreboards.remove(player.getUniqueId());
        if (scoreboard != null) {
            scoreboard.cleanup();
        }
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);

        for (Player p : event.getPlayer().getServer().getOnlinePlayers()) {
            p.sendMessage(
                    ChatColor.GREEN + LocaleManager.getMessage("player.left.server", p.locale(), player.getName()));
        }

        if (cookiePlayer != null) {
            cookiePlayer.disconnect();
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);

        if (event.getTo().getWorld().getName().equalsIgnoreCase("lobby")) {
            if (cookiePlayer != null && cookiePlayer.getState() != PlayerState.LOBBY) {
                cookiePlayer.setState(PlayerState.LOBBY);
            }
            if (!playerLobbyScoreboards.containsKey(player.getUniqueId())) {
                playerLobbyScoreboards.get(player.getUniqueId()).update();
            }
        } else {
            LobbyScoreboard scoreboard = playerLobbyScoreboards.remove(player.getUniqueId());
            if (scoreboard != null) {
                scoreboard.cleanup();
            }
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ocm mode old " + event.getPlayer().getName());
        if (cookiePlayer != null && event.getFrom().getWorld() != event.getTo().getWorld()) {
            cookiePlayer.resetPlayer();
        }
    }
}
