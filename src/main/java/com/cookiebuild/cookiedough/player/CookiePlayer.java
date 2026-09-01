package com.cookiebuild.cookiedough.player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.activity.ActivityRegistry;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

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
        ActivityRegistry.leave(this, "disconnect");
        var currentGame = GameManager.getGameOfPlayer(this);
        if (currentGame != null) {
            try {
                currentGame.removePlayer(this, "disconnect");
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
        this.player.closeInventory();
        this.player.getInventory().clear();
        this.player.getInventory().setArmorContents(null);
        this.player.getInventory().setItemInOffHand(null);
        this.player.setGameMode(GameMode.ADVENTURE);
        var maxHealth = this.player.getAttribute(Attribute.MAX_HEALTH);
        this.player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
        this.player.setAbsorptionAmount(0);
        this.player.setFoodLevel(20);
        this.player.setSaturation(20);
        this.player.setExhaustion(0);
        this.player.setFireTicks(0);
        this.player.setFreezeTicks(0);
        this.player.setFallDistance(0);
        this.player.setVelocity(new Vector());
        this.player.setLevel(0);
        this.player.setExp(0);
        this.player.setTotalExperience(0);
        this.player.setArrowsInBody(0);
        this.player.setAllowFlight(false);
        this.player.setFlying(false);
        this.player.setGliding(false);
        this.player.setInvulnerable(false);
        this.player.setCollidable(true);

        // Reset display name (chat)
        this.player.setDisplayName(this.player.getName());

        // Reset name tag above head
        this.player.setPlayerListName(this.player.getName());

        // Remove all potion effects
        for (PotionEffect effect : this.player.getActivePotionEffects()) {
            this.player.removePotionEffect(effect.getType());
        }
    }
}
