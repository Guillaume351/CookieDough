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

import java.util.Date;

public class PlayerWrapperListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Remove default join message
        event.setJoinMessage(null);

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
            }
        });
    }
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // TODO
        Player player = event.getPlayer();
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer != null) {
            cookiePlayer.disconnect();
        }
    }
}
