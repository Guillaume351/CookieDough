package com.cookiebuild.cookiedough.player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Objects;
import java.util.logging.Level;

public class CookiePlayer {
    final private Player player;

    private PlayerState state;

    public CookiePlayer(Player player) {
        this.player = player;
        this.state = PlayerState.LOBBY;  // Assume new players start in the lobby

        // Register player into plugin's player list
        PlayerManager.addPlayer(this);
    }

    public Player getPlayer() {
        return player;
    }

    public void disconnect() {
        if (this.state == PlayerState.IN_GAME) {
            try {
                Objects.requireNonNull(GameManager.getGameOfPlayer(this)).removePlayer(this);
                setState(PlayerState.OFFLINE);
            } catch (Exception e) {
                CookieDough.getInstance().getLogger().log(Level.SEVERE, "Error while removing player from game: " + e.getMessage());
            }
        }
        this.state = PlayerState.OFFLINE; // Set state to offline when the player disconnects
        PlayerManager.removePlayer(this);
    }

    public PlayerState getState() {
        return state;
    }

    public void setState(PlayerState state) {
        this.state = state;
    }

    public void resetPlayer() {
        this.player.getInventory().clear();
        this.player.setHealth(20);
        this.player.setFoodLevel(20);
        this.player.setSaturation(20);
        this.player.setFireTicks(0);
        // remove all potion effects
        for (PotionEffect effect : this.player.getActivePotionEffects()) {
            this.player.removePotionEffect(effect.getType());
        }
    }
}
