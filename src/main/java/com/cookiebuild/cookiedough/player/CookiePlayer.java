package com.cookiebuild.cookiedough.player;

import org.bukkit.entity.Player;

public class CookiePlayer {
    final private Player player;

    private boolean inGame = false;

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

    public boolean isInGame() {
        return inGame;
    }

    public void setInGame(boolean inGame) {
        this.inGame = inGame;
    }
}
