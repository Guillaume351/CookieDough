package com.cookiebuild.cookiedough.cosmetics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;

/**
 * Particle recipes of every visual cosmetic, kept free of Bukkit runtime state
 * so they can be reviewed and tested. Positions are relative to the player's
 * feet. Sounds are vanilla sound keys so no registry is needed here.
 */
final class CosmeticParticles {
    /**
     * Particles whose Java id has a native Bedrock mapping in Geyser's
     * {@code mappings/particles.json} (a vanilla {@code bedrockId} or a level
     * event, never an optional {@code geyseropt:} resource-pack particle), so
     * Bedrock players see the same cosmetic as Java players.
     */
    static final Set<Particle> BEDROCK_MAPPED = EnumSet.of(
            Particle.END_ROD, Particle.FALLING_HONEY, Particle.CRIT, Particle.NOTE, Particle.HEART,
            Particle.FIREWORK, Particle.DUST, Particle.CHERRY_LEAVES, Particle.SOUL_FIRE_FLAME,
            Particle.HAPPY_VILLAGER, Particle.ITEM, Particle.TOTEM_OF_UNDYING);

    /**
     * One {@code spawnParticle} call.
     *
     * @param data null, a {@link Particle.DustOptions} for DUST, or a
     *        {@link Material} that the runtime turns into an item stack for ITEM
     */
    record Burst(Particle particle, int count, double x, double y, double z,
            double spreadXZ, double spreadY, double speed, Object data) {
        Burst {
            if (particle == null || count < 1 || count > 64) {
                throw new IllegalArgumentException("A particle and a bounded count are required");
            }
        }

        static Burst at(Particle particle, int count, double height, double spreadXZ, double spreadY,
                double speed) {
            return new Burst(particle, count, 0, height, 0, spreadXZ, spreadY, speed, null);
        }

        Burst with(Object value) {
            return new Burst(particle, count, x, y, z, spreadXZ, spreadY, speed, value);
        }
    }

    /** A step of a victory animation, played {@code delayTicks} after the win. */
    record Frame(int delayTicks, List<Burst> bursts, String sound, float volume, float pitch) {
        Frame {
            bursts = List.copyOf(bursts);
        }
    }

    private static final Color CHOCOLATE = Color.fromRGB(92, 51, 23);
    private static final Color CREAM = Color.fromRGB(243, 217, 164);
    private static final List<Color> RAINBOW = List.of(
            Color.fromRGB(255, 59, 48), Color.fromRGB(255, 149, 0), Color.fromRGB(255, 214, 10),
            Color.fromRGB(52, 199, 89), Color.fromRGB(10, 132, 255), Color.fromRGB(94, 92, 230),
            Color.fromRGB(191, 90, 242));

    private static final Map<String, List<Burst>> TRAILS = Map.ofEntries(
            Map.entry(CosmeticCatalog.COOKIE_CRUMB_TRAIL,
                    List.of(Burst.at(Particle.FALLING_HONEY, 2, 0.3, 0.12, 0.02, 0.0))),
            Map.entry(CosmeticCatalog.COOKIE_SPARKLE_TRAIL,
                    List.of(Burst.at(Particle.END_ROD, 2, 0.25, 0.15, 0.05, 0.01))),
            Map.entry(CosmeticCatalog.STARTER_SPARK_TRAIL,
                    List.of(Burst.at(Particle.CRIT, 3, 0.2, 0.15, 0.05, 0.02))),
            Map.entry(CosmeticCatalog.NOTE_TRAIL,
                    List.of(Burst.at(Particle.NOTE, 1, 0.45, 0.2, 0.05, 1.0))),
            Map.entry(CosmeticCatalog.HEART_TRAIL,
                    List.of(Burst.at(Particle.HEART, 1, 0.45, 0.15, 0.05, 0.0))),
            Map.entry(CosmeticCatalog.STREAK_STAR_TRAIL,
                    List.of(Burst.at(Particle.FIREWORK, 1, 0.2, 0.08, 0.03, 0.01))),
            Map.entry(CosmeticCatalog.CHOCOLATE_CHIP_TRAIL, List.of(
                    Burst.at(Particle.DUST, 3, 0.15, 0.15, 0.04, 0.0)
                            .with(new Particle.DustOptions(CHOCOLATE, 1.1f)),
                    Burst.at(Particle.DUST, 1, 0.15, 0.15, 0.04, 0.0)
                            .with(new Particle.DustOptions(CREAM, 0.9f)))),
            Map.entry(CosmeticCatalog.CHERRY_PETAL_TRAIL,
                    List.of(Burst.at(Particle.CHERRY_LEAVES, 2, 0.9, 0.25, 0.1, 0.0))),
            Map.entry(CosmeticCatalog.SOUL_FLAME_TRAIL,
                    List.of(Burst.at(Particle.SOUL_FIRE_FLAME, 2, 0.1, 0.12, 0.02, 0.005))),
            Map.entry(CosmeticCatalog.LUCKY_CLOVER_TRAIL,
                    List.of(Burst.at(Particle.HAPPY_VILLAGER, 2, 0.25, 0.2, 0.05, 0.0))));

    private static final Map<String, List<Frame>> VICTORIES = Map.of(
            CosmeticCatalog.GOLDEN_COOKIE_BURST, List.of(new Frame(0,
                    List.of(Burst.at(Particle.FIREWORK, 18, 1.0, 0.8, 0.8, 0.04)),
                    "entity.firework_rocket.blast", 0.7f, 1.2f)),
            CosmeticCatalog.COOKIE_RAIN_VICTORY, cookieRain(),
            CosmeticCatalog.TOTEM_VICTORY, List.of(
                    new Frame(0, List.of(Burst.at(Particle.TOTEM_OF_UNDYING, 40, 1.2, 0.6, 0.8, 0.5)),
                            "item.totem.use", 0.35f, 1.3f),
                    new Frame(8, List.of(Burst.at(Particle.TOTEM_OF_UNDYING, 24, 2.0, 0.4, 0.4, 0.35)),
                            "entity.player.levelup", 0.5f, 1.8f)),
            CosmeticCatalog.FIREWORK_VICTORY, fireworkSpiral());

    private CosmeticParticles() {
    }

    /** Every cosmetic id that renders as a lobby trail. */
    static Set<String> trailIds() {
        Set<String> ids = new java.util.HashSet<>(TRAILS.keySet());
        ids.add(CosmeticCatalog.RAINBOW_TRAIL);
        return Set.copyOf(ids);
    }

    /**
     * Bursts for one trail step. {@code step} only changes colour cycling
     * trails; every other trail is deterministic.
     */
    static List<Burst> trail(String trailId, long step) {
        if (trailId == null) return List.of();
        if (CosmeticCatalog.RAINBOW_TRAIL.equals(trailId)) {
            Color color = RAINBOW.get((int) Math.floorMod(step, (long) RAINBOW.size()));
            return List.of(Burst.at(Particle.DUST, 3, 0.2, 0.15, 0.05, 0.0)
                    .with(new Particle.DustOptions(color, 1.3f)));
        }
        return TRAILS.getOrDefault(trailId, List.of());
    }

    /** The main particle of a trail, or null for an unknown id. */
    static Particle primaryTrailParticle(String trailId) {
        List<Burst> bursts = trail(trailId, 0);
        return bursts.isEmpty() ? null : bursts.getFirst().particle();
    }

    static boolean hasVictoryEffect(String cosmeticId) {
        return cosmeticId != null && VICTORIES.containsKey(cosmeticId);
    }

    static List<Frame> victory(String cosmeticId) {
        return cosmeticId == null ? List.of() : VICTORIES.getOrDefault(cosmeticId, List.of());
    }

    static Set<String> victoryIds() {
        return VICTORIES.keySet();
    }

    private static List<Frame> cookieRain() {
        List<Frame> frames = new ArrayList<>();
        frames.add(new Frame(0, List.of(Burst.at(Particle.HAPPY_VILLAGER, 12, 1.2, 0.8, 0.6, 0.0)),
                "entity.player.levelup", 0.6f, 1.6f));
        for (int step = 0; step < 4; step++) {
            frames.add(new Frame(step * 5, List.of(Burst.at(Particle.ITEM, 10, 2.6, 1.2, 0.3, 0.02)
                    .with(Material.COOKIE)), step == 2 ? "entity.item.pickup" : null, 0.5f, 0.8f));
        }
        return List.copyOf(frames);
    }

    /** Sparklers climbing a helix around the winner, then a burst above the head. */
    private static List<Frame> fireworkSpiral() {
        List<Frame> frames = new ArrayList<>();
        int points = 12;
        for (int index = 0; index < points; index++) {
            double angle = index * Math.PI / 3.0;
            double x = Math.cos(angle) * 0.8;
            double z = Math.sin(angle) * 0.8;
            double y = 0.2 + index * 0.25;
            frames.add(new Frame(index * 2, List.of(
                    new Burst(Particle.FIREWORK, 2, x, y, z, 0.05, 0.05, 0.0, null),
                    new Burst(Particle.END_ROD, 1, x, y, z, 0.02, 0.02, 0.0, null)),
                    index == 0 ? "entity.firework_rocket.launch" : null, 0.7f, 1.0f));
        }
        frames.add(new Frame(points * 2 + 2, List.of(Burst.at(Particle.FIREWORK, 24, 3.4, 0.7, 0.7, 0.08)),
                "entity.firework_rocket.large_blast", 0.7f, 1.0f));
        frames.add(new Frame(points * 2 + 6, List.of(), "entity.firework_rocket.twinkle", 0.6f, 1.0f));
        return List.copyOf(frames);
    }
}
