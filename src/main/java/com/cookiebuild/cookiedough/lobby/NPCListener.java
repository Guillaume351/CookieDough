package com.cookiebuild.cookiedough.lobby;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.service.PlayerStatsService;

public class NPCListener implements Listener {

    private final PlayerStatsService playerStatsService;

    public NPCListener(PlayerStatsService playerStatsService) {
        this.playerStatsService = playerStatsService;
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        if (event.getRightClicked() instanceof ArmorStand) {
            ArmorStand armorStand = (ArmorStand) event.getRightClicked();
            Player player = event.getPlayer();

            // Check if this armor stand is a statue
            if (isStatue(armorStand)) {
                event.setCancelled(true);

                // Get game mode from armor stand metadata
                String gameMode = null;
                if (armorStand.hasMetadata("gameMode")) {
                    gameMode = armorStand.getMetadata("gameMode").get(0).asString();
                }

                if (gameMode != null) {
                    // Get top player for this game mode
                    PlayerData topPlayer = playerStatsService.getTopPlayerThisWeek(gameMode);
                    if (topPlayer != null) {
                        // Get win count for the top player
                        int wins = playerStatsService.getWinsThisWeek(topPlayer.getId(), gameMode);
                        player.sendMessage(
                                "§6" + topPlayer.getName() + " is the top player this week with " + wins + " wins!");
                    } else {
                        player.sendMessage(
                                "§6This is the top player statue for " + gameMode + ", but no player was found!");
                    }
                } else {
                    player.sendMessage("§6This is the player with the most wins this week!");
                }
            }
        }
    }

    private boolean isStatue(ArmorStand armorStand) {
        // Check if armor stand has custom name indicating it's a statue
        return armorStand.getCustomName() != null &&
                armorStand.getCustomName().contains("Top Player This Week");
    }

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
