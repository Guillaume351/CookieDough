package com.cookiebuild.cookiedough.listener;

import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.EventHandler;

// Blocks common events (BlockBreakEvent, BlockPlaceEvent, etc.)
public abstract class BaseEventBlocker implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!shouldAllowBlockBreak(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!shouldAllowBlockPlace(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!shouldAllowPlayerInteract(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!shouldAllowEntityDamage(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!shouldAllowProjectileLaunch(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!shouldAllowPlayerDropItem(event)) {
            event.setCancelled(true);
        }
    }

    // Methods to be overridden by sub-plugins
    protected boolean shouldAllowBlockBreak(BlockBreakEvent event) {
        return false;
    }

    protected boolean shouldAllowBlockPlace(BlockPlaceEvent event) {
        return false;
    }

    protected boolean shouldAllowPlayerInteract(PlayerInteractEvent event) {
        return false;
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
}
