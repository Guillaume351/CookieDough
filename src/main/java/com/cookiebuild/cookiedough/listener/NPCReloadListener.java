package com.cookiebuild.cookiedough.listener;

import com.cookiebuild.cookiedough.lobby.GameNPC;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NPCReloadListener implements Listener {
    private final Map<String, GameNPC> gameNpcs;

    public NPCReloadListener() {
        this.gameNpcs = new HashMap<>();
    }

    public void registerNPC(GameNPC npc) {
        gameNpcs.put(npc.getGameName().toLowerCase(Locale.ROOT), npc);
        Chunk chunk = npc.getLocation().getChunk();
        chunk.setForceLoaded(true);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (GameNPC npc : gameNpcs.values()) {
            if (isLocationInChunk(npc.getLocation(), chunk)) {
                // If this chunk contains an NPC, keep it loaded
                chunk.setForceLoaded(true);
                return;
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (GameNPC npc : gameNpcs.values()) {
            if (isLocationInChunk(npc.getLocation(), chunk)) {
                npc.reconcileNpc(chunk);
            }
        }
    }

    private boolean isLocationInChunk(Location location, Chunk chunk) {
        return location.getWorld() != null && location.getWorld().getUID().equals(chunk.getWorld().getUID())
                && location.getBlockX() >> 4 == chunk.getX()
                && location.getBlockZ() >> 4 == chunk.getZ();
    }

}
