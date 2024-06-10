package com.cookiebuild.cookiedough.listener;

import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerWrapperListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // TODO
        Player player = event.getPlayer();

        new CookiePlayer(player);
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
