package com.cookiebuild.cookiedough.listener;

import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerWrapperListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Remove default join message
        event.setJoinMessage(null);

        Player player = event.getPlayer();

        new CookiePlayer(player);

        // Send welcome message
        player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("welcome.message", player.locale(), player.getName()));

        // Show it as a title
        player.sendTitle(ChatColor.translateAlternateColorCodes('&', LocaleManager.getMessage("welcome.message", player.locale(), player.getName())),
                "",
                10, 60, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
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
