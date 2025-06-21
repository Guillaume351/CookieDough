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
import com.cookiebuild.cookiedough.model.PlayerSession;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.cookiedough.utils.DiscordUtils;
import com.cookiebuild.cookiedough.utils.LocaleManager;

public class PlayerWrapperListener implements Listener {
    private static PlayerWrapperListener instance;

    private final Map<UUID, LobbyScoreboard> playerLobbyScoreboards = new HashMap<>();
    private final Map<UUID, PlayerSession> activePlayerSessions = new HashMap<>();
    private final PlayerStatsService playerStatsService;

    public PlayerWrapperListener() {
        this.playerStatsService = CookieDough.getPlayerStatsService();
        instance = this;

        // Schedule regular play time updates for active sessions
        Bukkit.getScheduler().runTaskTimerAsynchronously(CookieDough.getInstance(), () -> {
            long currentTime = new Date().getTime();
            for (Map.Entry<UUID, PlayerSession> entry : activePlayerSessions.entrySet()) {
                UUID playerId = entry.getKey();
                PlayerSession currentSession = entry.getValue();
                Player player = Bukkit.getPlayer(playerId);

                if (player != null && player.isOnline() && currentSession != null
                        && currentSession.getStartTime() != null) {
                    long duration = currentTime - currentSession.getStartTime().getTime();

                    Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
                        // Get a fresh copy of PlayerData for this update
                        PlayerData playerData = playerStatsService.getPlayerData(playerId);
                        if (playerData != null) {
                            // Find the current session in the fresh PlayerData instance
                            PlayerSession sessionToUpdate = playerData.getPlayerSessions().stream()
                                    .filter(s -> s.getStartTime() != null
                                            && s.getStartTime().equals(currentSession.getStartTime()))
                                    .findFirst()
                                    .orElse(null);

                            if (sessionToUpdate != null) {
                                sessionToUpdate.setDuration(duration);
                                sessionToUpdate.setServerCrash(true); // Mark as crash until player quits normally
                                GenericDAOImpl<PlayerData> playerDataDAO = new GenericDAOImpl<>(PlayerData.class);
                                playerDataDAO.update(playerData);

                                // Log duration update every 5 minutes to avoid spam
                                if (duration % (5 * 60 * 1000) < 30000) { // Within 30 seconds of 5-minute mark
                                    CookieDough.getInstance().getLogger()
                                            .info("Updated session duration for " + player.getName() +
                                                    " (Session ID: " + sessionToUpdate.getId() + ") - Duration: "
                                                    + (duration / 1000 / 60) + " minutes");
                                }
                            }
                        }
                    });
                }
            }
        }, 20 * 60, 20 * 60); // Update every 1 minute for crash recovery
    }

    /**
     * Get the login time (start time of current session) for a player
     *
     * @param playerId The UUID of the player
     * @return The login time or null if not found or session not active
     */
    public static Date getPlayerLoginTime(UUID playerId) {
        if (instance != null && instance.activePlayerSessions.containsKey(playerId)) {
            PlayerSession session = instance.activePlayerSessions.get(playerId);
            if (session != null) {
                return session.getStartTime();
            }
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

        // Check if server was empty before this player joined
        int onlinePlayerCount = Bukkit.getOnlinePlayers().size();
        if (onlinePlayerCount == 1) { // Only this player is online
            // Send Discord invitation message after a short delay to let the player settle
            // in
            Bukkit.getScheduler().runTaskLater(CookieDough.getInstance(), () -> {
                if (player.isOnline()) {
                    player.sendMessage(
                            ChatColor.YELLOW + LocaleManager.getMessage("server.empty_join_discord", player.locale()));
                }
            }, 60L); // 3 seconds delay (60 ticks)
        }

        // Player session handling
        Date joinTime = new Date();

        Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
            PlayerData playerData = playerStatsService.getPlayerData(player.getUniqueId());
            boolean newPlayer = false;
            if (playerData == null) {
                playerData = new PlayerData();
                playerData.setId(player.getUniqueId());
                playerData.setName(player.getName());
                playerData.setCreatedAt(joinTime);
                newPlayer = true;
            }
            playerData.setLastLogin(joinTime);

            PlayerSession newSession = new PlayerSession();
            newSession.setPlayerData(playerData);
            newSession.setStartTime(joinTime);
            newSession.setDuration(0L); // Initial duration
            newSession.setServerCrash(true); // Assume crash until normal quit

            playerData.addPlayerSession(newSession);
            activePlayerSessions.put(player.getUniqueId(), newSession);

            GenericDAOImpl<PlayerData> playerDataDAO = new GenericDAOImpl<>(PlayerData.class);
            if (newPlayer) {
                playerDataDAO.save(playerData);
                CookieDough.getInstance().getLogger().info("Player " + player.getName() + " (" + player.getUniqueId()
                        + ") created with new session ID: " + newSession.getId());
                String webhookUrl = System.getenv("DISCORD_NEW_PLAYER_WEBHOOK_URL");
                DiscordUtils.sendDiscordMessage(webhookUrl,
                        "A new player, " + player.getName() + ", has joined the server!");
            } else {
                playerDataDAO.update(playerData);
                CookieDough.getInstance().getLogger()
                        .info("Player " + player.getName() + " (" + player.getUniqueId() + ") started new session ID: "
                                + newSession.getId() + " (Total sessions: " + playerData.getPlayerSessions().size()
                                + ")");
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

        // Finalize player session
        PlayerSession finishedSession = activePlayerSessions.remove(player.getUniqueId());
        Date quitTime = new Date();

        if (finishedSession != null) {
            finishedSession.setEndTime(quitTime);
            if (finishedSession.getStartTime() != null) {
                finishedSession.setDuration(quitTime.getTime() - finishedSession.getStartTime().getTime());
            } else {
                // Should not happen if join logic is correct
                finishedSession.setDuration(0L);
            }
            finishedSession.setServerCrash(false); // Normal quit

            Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
                // Get a fresh copy of PlayerData for this update
                PlayerData playerData = playerStatsService.getPlayerData(player.getUniqueId());
                if (playerData != null) {
                    // Find the current session in the fresh PlayerData instance
                    PlayerSession sessionToUpdate = playerData.getPlayerSessions().stream()
                            .filter(s -> s.getStartTime() != null
                                    && s.getStartTime().equals(finishedSession.getStartTime()))
                            .findFirst()
                            .orElse(null);

                    if (sessionToUpdate != null) {
                        sessionToUpdate.setEndTime(quitTime);
                        sessionToUpdate.setDuration(quitTime.getTime() - sessionToUpdate.getStartTime().getTime());
                        sessionToUpdate.setServerCrash(false); // Normal quit
                        GenericDAOImpl<PlayerData> playerDataDAO = new GenericDAOImpl<>(PlayerData.class);
                        playerDataDAO.update(playerData);

                        long durationMinutes = sessionToUpdate.getDuration() / 1000 / 60;
                        CookieDough.getInstance().getLogger()
                                .info("Player " + player.getName() + " (" + player.getUniqueId() +
                                        ") session ended normally. Session ID: " + sessionToUpdate.getId() +
                                        ", Duration: " + sessionToUpdate.getDuration() + "ms (" + durationMinutes
                                        + " minutes)" +
                                        ", Total sessions: " + playerData.getPlayerSessions().size());
                    } else {
                        CookieDough.getInstance().getLogger().warning("Could not find session to update for player " +
                                player.getName() + " (" + player.getUniqueId() + ") in database on quit!");
                    }
                } else {
                    CookieDough.getInstance().getLogger().warning("Could not find PlayerData for player " +
                            player.getName() + " (" + player.getUniqueId() + ") in database on quit!");
                }
            });
        } else {
            CookieDough.getInstance().getLogger()
                    .warning("No active session found for player " + player.getName() + " on quit.");
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
