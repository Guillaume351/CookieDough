package com.cookiebuild.cookiedough.player;


import com.cookiebuild.cookiedough.CookieDough;
import org.bukkit.entity.Player;

public class CookiePlayer {
    private Player player;

    public CookiePlayer(Player player) {
        this.player = player;

        // Register player into plugin's player list
        PlayerManager.addPlayer(this);
    }

    public Player getPlayer() {
        return player;
    }

    public void disconnect() {
        PlayerManager.removePlayer(this);
    }
}
