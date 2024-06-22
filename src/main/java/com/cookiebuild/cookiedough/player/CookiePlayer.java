package com.cookiebuild.cookiedough.player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.logging.Level;

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
        if(inGame) {
            try {
                Objects.requireNonNull(GameManager.getGameOfPlayer(this)).removePlayer(this);
            }
            catch (Exception e) {
                CookieDough.getInstance().getLogger().log(Level.SEVERE, "Error while removing player from game: " + e.getMessage());
            }
        }
        PlayerManager.removePlayer(this);
    }

    public boolean isInGame() {
        return inGame;
    }

    public void setInGame(boolean inGame) {
        this.inGame = inGame;
    }
}
