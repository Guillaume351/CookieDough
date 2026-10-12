package com.cookiebuild.cookiedough.theme.music;

import java.util.List;

/**
 * Where a theme's music box plays and how far it carries. Volume falls off
 * linearly from {@code maxGain} at the box to silence at {@code radius}, so
 * the music stays soft but can be heard from across the lobby.
 */
public record MusicBoxPlan(String world, double x, double y, double z, float yaw, double radius, float maxGain,
        int gapTicks, List<String> playlist) {
    public MusicBoxPlan {
        if (radius <= 0 || maxGain <= 0 || maxGain > 1 || gapTicks < 0) {
            throw new IllegalArgumentException("Invalid music box settings");
        }
        playlist = List.copyOf(playlist);
    }

    /** Gain for a listener at {@code distance} blocks, 0 when out of range. */
    public float gainAt(double distance) {
        if (distance >= radius) return 0f;
        double closeness = 1.0 - Math.max(0, distance) / radius;
        return (float) (maxGain * closeness);
    }
}
