package com.cookiebuild.cookiedough.lobby;

import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class NPCListener implements Listener {

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Zombie) {
            for (GameNPC npc : LobbyManager.getInstance().getGameNpcs()) {
                if (npc.getNPC().equals(event.getRightClicked())) {
                    event.setCancelled(true);
                    npc.interactWithPlayer(event.getPlayer());
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Zombie) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof Zombie zombie) {

            for (GameNPC npc : LobbyManager.getInstance().getGameNpcs()) {
                if (npc.getNPC().equals(zombie)) {
                    event.setCancelled(true);
                    npc.interactWithPlayer(player);
                    break;
                }
            }
        }
    }

    // prevent from despawning
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Zombie) {
            event.getEntity().setHealth(20);
            event.setCancelled(true);
        }
    }
}
