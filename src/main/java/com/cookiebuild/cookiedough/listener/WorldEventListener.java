package com.cookiebuild.cookiedough.listener;

import org.bukkit.GameRules;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.function.LongConsumer;

public class WorldEventListener implements Listener {

    public WorldEventListener() {
        // The lobby is already loaded before plugin listeners are registered, so it
        // does not receive a WorldLoadEvent during a normal startup.
        Bukkit.getWorlds().forEach(WorldEventListener::configureWorld);
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        configureWorld(event.getWorld());
    }

    @EventHandler
    public void onBlockTick(BlockPhysicsEvent event) {
        if (WorldPolicy.usesFrozenPhysics(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockUpdate(BlockFromToEvent event) {
        if (WorldPolicy.usesFrozenPhysics(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        boolean customSpawn = event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM;
        if (WorldPolicy.blocksHostileSpawn(event.getLocation().getWorld().getName(),
                event.getEntity() instanceof Enemy, customSpawn)) {
            event.setCancelled(true);
            return;
        }
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            event.setCancelled(true);
        }
    }

    private static void configureWorld(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, Boolean.FALSE);
        world.setGameRule(GameRules.ADVANCE_WEATHER, Boolean.FALSE);
        if (WorldPolicy.usesFrozenPhysics(world.getName())) {
            world.setGameRule(GameRules.SPAWN_MOBS, Boolean.FALSE);
            world.setGameRule(GameRules.SPAWN_MONSTERS, Boolean.FALSE);
        }
        setTimeIfSupported(world::setTime, 0);
        world.setAutoSave(false);
    }

    static boolean setTimeIfSupported(LongConsumer timeSetter, long time) {
        try {
            // Paper 26.1 rejects setTime for dimensions without a world clock
            // (for example, Nether worlds). Their fixed dimension time needs no
            // adjustment, while the remaining lobby protections still apply.
            timeSetter.accept(time);
            return true;
        } catch (IllegalArgumentException ignored) {
            // Expected for worlds whose dimension type has no world clock.
            return false;
        }
    }
}
