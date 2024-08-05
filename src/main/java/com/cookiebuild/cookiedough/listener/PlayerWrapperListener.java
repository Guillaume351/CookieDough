package com.cookiebuild.cookiedough.listener;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.dao.GenericDAOImpl;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Date;

public class PlayerWrapperListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Remove default join message
        event.setJoinMessage(null); // TODO: remove this when we have a proper join message

        // send player join message using LocaleManager
        for (Player player : event.getPlayer().getServer().getOnlinePlayers()) {
            player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("player.joined.server", player.locale(), player.getName()));
        }

        Player player = event.getPlayer();

        CookiePlayer cookiePlayer = new CookiePlayer(player);

        // Teleport player to lobby
        LobbyManager.teleportPlayerToLobby(cookiePlayer);


        // Send welcome message
        player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("welcome.message", player.locale(), player.getName()));

        // Show it as a title
        player.sendTitle(ChatColor.translateAlternateColorCodes('&', LocaleManager.getMessage("welcome.message", player.locale(), player.getName())),
                "",
                10, 60, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);


        // create async task to save player data
        Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
            // save player data
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
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);

        for (Player p : event.getPlayer().getServer().getOnlinePlayers()) {
            p.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("player.left.server", p.locale(), player.getName()));
        }

        if (cookiePlayer != null) {
            cookiePlayer.disconnect();
        }
    }

    // on world change, reset all states (inventory, health, etc.)
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // TODO: remove this when we have a proper teleport system
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ocm mode old " + event.getPlayer().getName());
        Player player = event.getPlayer();
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer != null && event.getFrom().getWorld() != event.getTo().getWorld()) {
            cookiePlayer.resetPlayer();
        }
    }
}
