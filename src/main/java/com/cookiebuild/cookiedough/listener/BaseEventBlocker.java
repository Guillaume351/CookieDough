package com.cookiebuild.cookiedough.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;

// Blocks common events (BlockBreakEvent, BlockPlaceEvent, etc.)
public class BaseEventBlocker implements Listener {

    public List<String> protectedWorlds = List.of("lobby");

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (protectedWorlds.contains(event.getBlock().getWorld().getName()) && !shouldAllowBlockBreak(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (protectedWorlds.contains(event.getBlock().getWorld().getName()) && !shouldAllowBlockPlace(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (protectedWorlds.contains(event.getPlayer().getWorld().getName()) && !shouldAllowPlayerInteract(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (protectedWorlds.contains(event.getEntity().getWorld().getName()) && !shouldAllowEntityDamage(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (protectedWorlds.contains(event.getEntity().getWorld().getName()) && !shouldAllowProjectileLaunch(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (protectedWorlds.contains(event.getPlayer().getWorld().getName()) && !shouldAllowPlayerDropItem(event)) {
            event.setCancelled(true);
        }
    }

    // Prevent hunger / saturation changes
    @EventHandler
    public void onPlayerChangeFoodLevel(FoodLevelChangeEvent event) {
        if (protectedWorlds.contains(event.getEntity().getWorld().getName()) && !shouldAllowPlayerChangeFoodLevel(event) && protectedWorlds.contains(event.getEntity().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    protected boolean shouldAllowPlayerChangeFoodLevel(FoodLevelChangeEvent event) {
        return false;
    }

    // Methods to be overridden by sub-plugins
    protected boolean shouldAllowBlockBreak(BlockBreakEvent event) {
        return canEditLobby(event.getPlayer());
    }

    protected boolean shouldAllowBlockPlace(BlockPlaceEvent event) {
        return canEditLobby(event.getPlayer());
    }

    protected boolean shouldAllowPlayerInteract(PlayerInteractEvent event) {
        return canEditLobby(event.getPlayer());
    }

    protected boolean shouldAllowEntityDamage(EntityDamageEvent event) {
        return false;
    }

    protected boolean shouldAllowProjectileLaunch(ProjectileLaunchEvent event) {
        return false;
    }

    protected boolean shouldAllowPlayerDropItem(PlayerDropItemEvent event) {
        return false;
    }

    private static boolean canEditLobby(Player player) {
        return canEditLobby(player.getWorld().getName(), player.isOp(), player.getGameMode());
    }

    static boolean canEditLobby(String worldName, boolean operator, GameMode gameMode) {
        return "lobby".equalsIgnoreCase(worldName) && operator && gameMode == GameMode.CREATIVE;
    }
}
