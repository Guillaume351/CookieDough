package com.cookiebuild.cookiedough.theme.decor;

import java.util.function.Consumer;

import org.bukkit.World;
import org.bukkit.entity.Player;

/** One layer of client-side blocks (static decor, per-player collectibles, lazily computed palettes). */
public interface FakeBlockSource {
    /** Adds the fake blocks of one chunk that this viewer should see. Main thread only. */
    void collect(Player viewer, World world, int chunkX, int chunkZ, Consumer<FakeBlock> out);

    /** The fake block this viewer sees at a position, or null. Main thread only. */
    FakeBlock at(Player viewer, World world, int x, int y, int z);
}
