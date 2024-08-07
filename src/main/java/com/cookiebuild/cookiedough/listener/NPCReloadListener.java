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
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Zombie zombie) {
                for (String gameName : npcLocations.keySet()) {
                    if (zombie.getPersistentDataContainer().has(new NamespacedKey(plugin, gameName), PersistentDataType.BYTE)) {
                        zombie.remove();
                        break;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Map.Entry<String, Location> entry : npcLocations.entrySet()) {
            Location loc = entry.getValue();
            if (isLocationInChunk(loc, chunk)) {
                new GameNPC(entry.getKey(), loc, plugin);
            }
        }
    }

    private boolean isLocationInChunk(Location location, Chunk chunk) {
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();
        int chunkX = chunk.getX() << 4; // Multiply by 16
        int chunkZ = chunk.getZ() << 4; // Multiply by 16

        return blockX >= chunkX && blockX < chunkX + 16 &&
                blockZ >= chunkZ && blockZ < chunkZ + 16 &&
                location.getWorld().equals(chunk.getWorld());
    }
}