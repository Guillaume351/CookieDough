package com.cookiebuild.cookiedough.theme.music;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Particle;

/**
 * The NPC that stands at the music box: a witch at Halloween, a snow golem
 * without its pumpkin at Christmas. Always non-persistent, AI off, silent and
 * invulnerable.
 *
 * @param type {@code witch} or {@code snow_golem}
 * @param particle shown above it while a song plays
 * @param nameKey nameplate key under the theme's {@code music.} messages (e.g. {@code witch})
 * @param nameColor a named text colour (e.g. {@code dark_purple})
 */
public record Musician(String type, Particle particle, String nameKey, String nameColor) {
    public static final String WITCH = "witch";
    public static final String SNOW_GOLEM = "snow_golem";
    private static final Set<String> TYPES = Set.of(WITCH, SNOW_GOLEM);
    private static final Set<String> COLORS = Set.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow",
            "white");

    /** Halloween's musician, also the default of a theme file that names none. */
    public static final Musician HALLOWEEN = new Musician(WITCH, Particle.WITCH, "witch", "dark_purple");

    public Musician {
        type = type == null ? WITCH : type.trim().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unknown musician " + type + " " + TYPES);
        if (particle == null) throw new IllegalArgumentException("Musician needs a particle");
        if (nameKey == null || !nameKey.matches("[a-z0-9_.-]{1,32}")) {
            throw new IllegalArgumentException("Bad musician name key " + nameKey);
        }
        nameColor = nameColor == null ? "dark_purple" : nameColor.trim().toLowerCase(Locale.ROOT);
        if (!COLORS.contains(nameColor)) throw new IllegalArgumentException("Unknown colour " + nameColor);
    }
}
