package com.cookiebuild.cookiedough.theme.music;

import java.util.List;

/**
 * Where a theme's music box plays, how far it carries and how long it stays
 * silent between two songs. Volume falls off
 * linearly from {@code maxGain} at the box to silence at {@code radius}, so
 * the music stays soft but can be heard from across the lobby.
 */
public record MusicBoxPlan(String world, double x, double y, double z, float yaw, double radius, float maxGain,
        int minGapTicks, int maxGapTicks, List<String> playlist) {
    public MusicBoxPlan {
        if (radius <= 0 || maxGain <= 0 || maxGain > 1 || minGapTicks < 0 || maxGapTicks < minGapTicks) {
            throw new IllegalArgumentException("Invalid music box settings");
        }
        playlist = List.copyOf(playlist);
    }

    /** Silence before the next song, so the music never plays non-stop. */
    public int gapTicks(java.util.random.RandomGenerator random) {
        return minGapTicks == maxGapTicks ? minGapTicks : random.nextInt(minGapTicks, maxGapTicks + 1);
    }

    /** Gain for a listener at {@code distance} blocks, 0 when out of range. */
    public float gainAt(double distance) {
        if (distance >= radius) return 0f;
        double closeness = 1.0 - Math.max(0, distance) / radius;
        return (float) (maxGain * closeness);
    }
}
