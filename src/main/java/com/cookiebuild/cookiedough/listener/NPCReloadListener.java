package com.cookiebuild.cookiedough.listener;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.lobby.GameNPC;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

public class NPCReloadListener implements Listener {
    private final CookieDough plugin;
    private final Map<String, Location> npcLocations;

    public NPCReloadListener(CookieDough plugin) {
        this.plugin = plugin;
        this.npcLocations = new HashMap<>();
    }

    public void registerNPC(String gameName, Location location) {
        npcLocations.put(gameName, location);
        Chunk chunk = location.getChunk();
        chunk.setForceLoaded(true);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (Map.Entry<String, Location> entry : npcLocations.entrySet()) {
            if (isLocationInChunk(entry.getValue(), chunk)) {
                // If this chunk contains an NPC, keep it loaded
                chunk.setForceLoaded(true);
                return;
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Map.Entry<String, Location> entry : npcLocations.entrySet()) {
            if (isLocationInChunk(entry.getValue(), chunk)) {
                // Respawn the NPC if it's not present
                if (!isNPCPresent(chunk, entry.getKey())) {
                    new GameNPC(entry.getKey(), entry.getValue(), plugin);
                }
            }
        }
    }

    private boolean isLocationInChunk(Location location, Chunk chunk) {
        return location.getChunk().equals(chunk);
    }

    private boolean isNPCPresent(Chunk chunk, String gameName) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Zombie && entity.getPersistentDataContainer().has(new NamespacedKey(plugin, gameName), PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }

}