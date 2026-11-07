package com.cookiebuild.cookiedough.theme;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Particle;

/**
 * Particles a theme file may use. About 75 % of players are on Bedrock: only
 * particles that Geyser maps to a native Bedrock particle or level event are
 * allowed (none mapped to {@code geyseropt:}, such as ASH or WHITE_ASH).
 */
public final class ThemeParticles {
    /** Java particles with a native Bedrock mapping in Geyser 2.12.1 (mappings/particles.json). */
    public static final Set<Particle> BEDROCK_SAFE = EnumSet.of(
            Particle.SNOWFLAKE, Particle.ITEM_SNOWBALL, Particle.END_ROD, Particle.FIREWORK, Particle.DUST,
            Particle.NOTE, Particle.HAPPY_VILLAGER, Particle.TOTEM_OF_UNDYING, Particle.GLOW,
            Particle.ELECTRIC_SPARK, Particle.HEART, Particle.WITCH, Particle.SOUL, Particle.SOUL_FIRE_FLAME,
            Particle.ITEM, Particle.CRIT, Particle.CHERRY_LEAVES);

    private ThemeParticles() {
    }

    /** Parses a particle name of a theme file; rejects unknown or Bedrock-less particles. */
    public static Particle parse(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A particle name is required");
        Particle particle;
        try {
            particle = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown particle " + name);
        }
        if (!BEDROCK_SAFE.contains(particle)) {
            throw new IllegalArgumentException("Particle " + particle + " has no native Bedrock mapping");
        }
        return particle;
    }
}
