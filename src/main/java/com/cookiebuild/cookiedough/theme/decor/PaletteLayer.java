package com.cookiebuild.cookiedough.theme.decor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Same-shape material swaps computed lazily, chunk by chunk, in disposable
 * match worlds (glowstone shown as jack o'lanterns...). Results are cached per
 * world and shared by every player of the match.
 */
public final class PaletteLayer implements FakeBlockSource, Listener {
    private final Map<Material, String> swaps;
    private final List<BlockData> probes = new ArrayList<>();
    private final Predicate<World> applies;
    private final Map<UUID, Map<Long, List<FakeBlock>>> cache = new HashMap<>();
    private final Map<UUID, Map<Long, FakeBlock>> positions = new HashMap<>();

    public PaletteLayer(Map<Material, String> swaps, Predicate<World> applies) {
        this.swaps = Map.copyOf(swaps);
        this.applies = applies;
        for (Material material : swaps.keySet()) probes.add(Bukkit.createBlockData(material));
    }

    public boolean isEmpty() {
        return swaps.isEmpty();
    }

    @Override
    public void collect(Player viewer, World world, int chunkX, int chunkZ, Consumer<FakeBlock> out) {
        if (swaps.isEmpty() || !applies.test(world)) return;
        compute(world, chunkX, chunkZ).forEach(out);
    }

    @Override
    public FakeBlock at(Player viewer, World world, int x, int y, int z) {
        Map<Long, FakeBlock> known = positions.get(world.getUID());
        return known == null ? null : known.get(FakeBlock.pack(x, y, z));
    }

    private List<FakeBlock> compute(World world, int chunkX, int chunkZ) {
        Map<Long, List<FakeBlock>> chunks = cache.computeIfAbsent(world.getUID(), id -> new HashMap<>());
        long key = FakeBlock.chunkKey(chunkX, chunkZ);
        List<FakeBlock> cached = chunks.get(key);
        if (cached != null) return cached;
        List<FakeBlock> found = new ArrayList<>();
        ChunkSnapshot snapshot = world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false);
        if (probes.stream().anyMatch(snapshot::contains)) {
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            for (int sectionY = minY >> 4; sectionY < maxY >> 4; sectionY++) {
                if (snapshot.isSectionEmpty(sectionY - (minY >> 4))) continue;
                for (int y = sectionY << 4; y < (sectionY << 4) + 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            Material type = snapshot.getBlockType(x, y, z);
                            String target = swaps.get(type);
                            if (target != null) {
                                found.add(new FakeBlock((chunkX << 4) + x, y, (chunkZ << 4) + z, target, type.name()));
                            }
                        }
                    }
                }
            }
        }
        List<FakeBlock> result = List.copyOf(found);
        chunks.put(key, result);
        Map<Long, FakeBlock> index = positions.computeIfAbsent(world.getUID(), id -> new HashMap<>());
        for (FakeBlock block : result) index.put(block.packedPosition(), block);
        return result;
    }

    /** Forgets every computed chunk and returns the fakes that were shown. */
    public List<FakeBlock> clear(World world) {
        positions.remove(world.getUID());
        Map<Long, List<FakeBlock>> removed = cache.remove(world.getUID());
        return removed == null ? List.of() : removed.values().stream().flatMap(List::stream).toList();
    }

    public void clearAll() {
        cache.clear();
        positions.clear();
    }

    @EventHandler
    public void onUnload(WorldUnloadEvent event) {
        clear(event.getWorld());
    }
}
