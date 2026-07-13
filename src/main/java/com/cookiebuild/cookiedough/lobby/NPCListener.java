package com.cookiebuild.cookiedough.lobby;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class NPCListener implements Listener {
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        handleNpcInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleNpcInteraction(event.getPlayer(), event.getRightClicked(), event);
    }

    /**
     * Paper 26 fires this before checking whether the attacked entity can take
     * damage. Geyser translates a Bedrock entity hit to the same Java attack
     * packet, so this still fires for our invulnerable zombie NPCs even when an
     * EntityDamageByEntityEvent is never produced.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        GameNPC npc = resolveNpc(event.getAttacked());
        if (npc != null) {
            event.setCancelled(true);
            npc.interactWithPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (resolveNpc(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        GameNPC npc = resolveNpc(event.getEntity());
        if (npc == null) {
            return;
        }

        event.setCancelled(true);
        Player player = extractDamagingPlayer(event.getDamager());
        if (player != null) {
            npc.interactWithPlayer(player);
        }
    }

    // prevent from despawning
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (resolveNpc(event.getEntity()) != null) {
            event.getEntity().setHealth(20);
            event.setCancelled(true);
        }
    }

    private void handleNpcInteraction(Player player, Entity clickedEntity, Cancellable event) {
        GameNPC npc = resolveNpc(clickedEntity);
        if (npc == null) {
            return;
        }

        event.setCancelled(true);
        npc.interactWithPlayer(player);
    }

    private Player extractDamagingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }

        return null;
    }

    private GameNPC resolveNpc(Entity entity) {
        if (!(entity instanceof Zombie zombie)) {
            return null;
        }

        LobbyManager lobbyManager = LobbyManager.getInstance();
        if (lobbyManager == null) {
            return null;
        }

        for (GameNPC npc : lobbyManager.getGameNpcs()) {
            Zombie trackedNpc = npc.getNPC();
            if (trackedNpc != null && trackedNpc.getUniqueId().equals(zombie.getUniqueId())) {
                return npc;
            }

            if (npc.matches(zombie)) {
                return npc;
            }
        }

        return null;
    }
}
