package com.cookiebuild.cookiedough.theme.decor;

/**
 * A block shown to clients only. {@code expected} is the real block type the
 * fake sits on ({@code AIR} for any air): the fake is skipped whenever the
 * world no longer matches, so a later real change never leaves a ghost block.
 */
public record FakeBlock(int x, int y, int z, String data, String expected) {
    public static final String AIR = "AIR";

    public FakeBlock {
        if (data == null || data.isBlank() || expected == null || expected.isBlank()) {
            throw new IllegalArgumentException("A fake block needs its data and the expected real type");
        }
    }

    public long packedPosition() {
        return pack(x, y, z);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
    }
}
