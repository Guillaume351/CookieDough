package com.cookiebuild.cookiedough.theme.decor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.World;
import org.bukkit.entity.Player;

/** The same fake blocks for every viewer of a world (the planned lobby decor). */
public final class StaticDecorLayer implements FakeBlockSource {
    private final Map<UUID, Map<Long, List<FakeBlock>>> byChunk = new HashMap<>();
    private final Map<UUID, Map<Long, FakeBlock>> byPosition = new HashMap<>();

    public void set(World world, List<FakeBlock> blocks) {
        Map<Long, List<FakeBlock>> chunks = new HashMap<>();
        Map<Long, FakeBlock> positions = new HashMap<>();
        for (FakeBlock block : blocks) {
            chunks.computeIfAbsent(FakeBlock.chunkKey(block.chunkX(), block.chunkZ()), key -> new java.util.ArrayList<>())
                    .add(block);
            positions.put(block.packedPosition(), block);
        }
        byChunk.put(world.getUID(), chunks);
        byPosition.put(world.getUID(), positions);
    }

    /** Removes a world's decor and returns what was shown, so the caller can restore real blocks. */
    public List<FakeBlock> clear(World world) {
        byPosition.remove(world.getUID());
        Map<Long, List<FakeBlock>> removed = byChunk.remove(world.getUID());
        return removed == null ? List.of() : removed.values().stream().flatMap(List::stream).toList();
    }

    public int size(World world) {
        Map<Long, FakeBlock> positions = byPosition.get(world.getUID());
        return positions == null ? 0 : positions.size();
    }

    @Override
    public void collect(Player viewer, World world, int chunkX, int chunkZ, Consumer<FakeBlock> out) {
        Map<Long, List<FakeBlock>> chunks = byChunk.get(world.getUID());
        if (chunks == null) return;
        List<FakeBlock> blocks = chunks.get(FakeBlock.chunkKey(chunkX, chunkZ));
        if (blocks != null) blocks.forEach(out);
    }

    @Override
    public FakeBlock at(Player viewer, World world, int x, int y, int z) {
        Map<Long, FakeBlock> positions = byPosition.get(world.getUID());
        return positions == null ? null : positions.get(FakeBlock.pack(x, y, z));
    }
}
