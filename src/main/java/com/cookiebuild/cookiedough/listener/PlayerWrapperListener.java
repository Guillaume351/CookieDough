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
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.cookiedough.utils.DiscordUtils;
import com.cookiebuild.cookiedough.utils.LocaleManager;

public class PlayerWrapperListener implements Listener {
    private static PlayerWrapperListener instance;

    private final Map<UUID, LobbyScoreboard> playerLobbyScoreboards = new HashMap<>();
    private final Map<UUID, Date> playerLoginTimes = new HashMap<>();
    private final PlayerStatsService playerStatsService;

    public PlayerWrapperListener() {
        this.playerStatsService = CookieDough.getPlayerStatsService();
        instance = this;

        // Schedule regular play time updates
        Bukkit.getScheduler().runTaskTimerAsynchronously(CookieDough.getInstance(), () -> {
            for (UUID playerId : playerLoginTimes.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    long playTime = new Date().getTime() - playerLoginTimes.get(playerId).getTime();
                    Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
                        PlayerData playerData = playerStatsService.getPlayerData(playerId);
                        if (playerData != null) {
                            playerData.addPlayTime(playTime);
                            GenericDAOImpl<PlayerData> playerDataDAO = new GenericDAOImpl<>(PlayerData.class);
                            playerDataDAO.update(playerData);
                        }
                    });
                }
            }
        }, 20 * 60 * 5, 20 * 60 * 5); // Update every 5 minutes (5 * 60 * 20 ticks)
    }

    /**
     * Get the login time for a player
     *
     * @param playerId The UUID of the player
     * @return The login time or null if not found
     */
    public static Date getPlayerLoginTime(UUID playerId) {
        if (instance != null && instance.playerLoginTimes.containsKey(playerId)) {
            return instance.playerLoginTimes.get(playerId);
        }
        return null;
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

        // Record login time
        playerLoginTimes.put(player.getUniqueId(), new Date());

        Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
            PlayerData playerData = playerStatsService.getPlayerData(player.getUniqueId());
            if (playerData == null) {
                playerData = new PlayerData();
                playerData.setId(player.getUniqueId());
                playerData.setName(player.getName());
                playerData.setCreatedAt(new Date());
                playerData.setLastLogin(new Date());
                GenericDAOImpl<PlayerData> playerDataDAO = new GenericDAOImpl<>(PlayerData.class);
                playerDataDAO.save(playerData);
                CookieDough.getInstance().getLogger().info("Player " + player.getName() + " created");

                String webhookUrl = System.getenv("DISCORD_NEW_PLAYER_WEBHOOK_URL");

                DiscordUtils.sendDiscordMessage(webhookUrl,
                        "A new player, " + player.getName() + ", has joined the server!");
            } else {
                playerData.setLastLogin(new Date());
                GenericDAOImpl<PlayerData> playerDataDAO = new GenericDAOImpl<>(PlayerData.class);
                playerDataDAO.update(playerData);
            }
        });

        // Send Discord notification for player join
        String playerStatusWebhookUrl = System.getenv("DISCORD_PLAYER_STATUS_WEBHOOK_URL");
        if (playerStatusWebhookUrl != null && !playerStatusWebhookUrl.isEmpty()) {
            int playerCount = Bukkit.getServer().getOnlinePlayers().size();
            String message = player.getName() + " has joined the server. Online players: " + playerCount;
            DiscordUtils.sendDiscordMessage(playerStatusWebhookUrl, message);
        }
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

        // Calculate play time
        Date loginTime = playerLoginTimes.remove(player.getUniqueId());
        if (loginTime != null) {
            long playTime = new Date().getTime() - loginTime.getTime();
            Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
                PlayerData playerData = playerStatsService.getPlayerData(player.getUniqueId());
                if (playerData != null) {
                    playerData.addPlayTime(playTime);
                    GenericDAOImpl<PlayerData> playerDataDAO = new GenericDAOImpl<>(PlayerData.class);
                    playerDataDAO.update(playerData);
                }
            });
        }

        for (Player p : event.getPlayer().getServer().getOnlinePlayers()) {
            // Make sure to exclude the quitting player from the list before sending the
            // message
            if (!p.getUniqueId().equals(player.getUniqueId())) {
                p.sendMessage(
                        ChatColor.GREEN + LocaleManager.getMessage("player.left.server", p.locale(), player.getName()));
            }
        }

        if (cookiePlayer != null) {
            cookiePlayer.disconnect();
        }

        // Send Discord notification for player quit
        String playerStatusWebhookUrl = System.getenv("DISCORD_PLAYER_STATUS_WEBHOOK_URL");
        if (playerStatusWebhookUrl != null && !playerStatusWebhookUrl.isEmpty()) {
            // Subtract 1 because the player has already left at this point for the count
            int playerCount = Bukkit.getServer().getOnlinePlayers().size() - 1;
            String message = player.getName() + " has left the server. Online players: " + playerCount;
            DiscordUtils.sendDiscordMessage(playerStatusWebhookUrl, message);
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ocm mode old " + event.getPlayer().getName());
        if (cookiePlayer != null && event.getFrom().getWorld() != event.getTo().getWorld()) {
            cookiePlayer.resetPlayer();
        }
    }
}
