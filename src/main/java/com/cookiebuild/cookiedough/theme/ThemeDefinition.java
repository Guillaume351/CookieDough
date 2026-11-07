package com.cookiebuild.cookiedough.theme;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

import com.cookiebuild.cookiedough.theme.advent.AdventCalendar;
import com.cookiebuild.cookiedough.theme.decor.DecorRules;
import com.cookiebuild.cookiedough.theme.hunt.HuntPlan;
import com.cookiebuild.cookiedough.theme.music.MusicBoxPlan;
import com.cookiebuild.cookiedough.theme.music.Musician;

/**
 * A parsed {@code themes/<id>/theme.yml}. Everything a season changes is data
 * here; the code only knows how to show it without touching any world.
 *
 * @param messagePrefix prefix of this theme's keys in the message bundles
 *        (e.g. {@code theme.halloween} for {@code theme.halloween.tab.header})
 */
public record ThemeDefinition(String id, ZonedDateTime start, ZonedDateTime end, String messagePrefix,
        Lobby lobby, MusicBoxPlan music, HuntPlan hunt, Games games, Texts texts, List<String> buildBattleThemes,
        AdventCalendar advent) {

    /**
     * Lobby ambience: client time, decor area and rules, bats, ambient sounds,
     * particles drifting from some decor blocks and an optional snowfall.
     */
    public record Lobby(String world, int radius, int minY, int maxY, long playerTime, DecorRules decor,
            int bats, List<String> ambientSounds, int ambientMinSeconds, int ambientMaxSeconds,
            List<AnchorParticle> anchorParticles, Snowfall snowfall) {
        public Lobby {
            ambientSounds = List.copyOf(ambientSounds);
            anchorParticles = List.copyOf(anchorParticles);
            if (radius < 1 || radius > 256 || minY > maxY) throw new IllegalArgumentException("Bad lobby decor area");
            if (bats < 0 || bats > 32) throw new IllegalArgumentException("bats must be within 0..32");
            if (ambientMinSeconds < 5 || ambientMaxSeconds < ambientMinSeconds) {
                throw new IllegalArgumentException("Bad ambient sound interval");
            }
        }
    }

    /**
     * Particles shown, to each nearby player only, at decor blocks whose data
     * contains one of {@code near} (e.g. souls above jack o'lanterns).
     */
    public record AnchorParticle(List<String> near, Particle particle, double dy, double spreadXZ, double spreadY,
            double speed) {
        public AnchorParticle {
            near = List.copyOf(near);
            if (near.isEmpty() || particle == null) throw new IllegalArgumentException("Bad anchor particle");
        }

        public boolean anchors(String blockData) {
            for (String part : near) if (blockData.contains(part)) return true;
            return false;
        }
    }

    /** Halloween's soul particles: the default when a theme file has no {@code lobby.anchor-particles}. */
    public static final List<AnchorParticle> HALLOWEEN_ANCHOR_PARTICLES = List.of(
            new AnchorParticle(List.of("jack_o_lantern"), Particle.SOUL, 0.9, 0.15, 0.1, 0.01),
            new AnchorParticle(List.of("hanging_moss", "cobweb"), Particle.SOUL_FIRE_FLAME, -0.3, 0.2, 0.1, 0.003));

    /**
     * Client-side snowfall: every half second each lobby player gets
     * {@code count} particles in a box of {@code radius} around them, between
     * {@code minHeight} and {@code maxHeight} above their feet. One packet per
     * player; no weather change.
     */
    public record Snowfall(Particle particle, int count, double radius, double minHeight, double maxHeight) {
        public Snowfall {
            if (particle == null || count < 1 || count > 40 || radius < 1 || radius > 24 || minHeight < 0
                    || maxHeight < minHeight || maxHeight > 32) {
                throw new IllegalArgumentException("Bad snowfall (count 1..40, radius 1..24, height 0..32)");
            }
        }
    }

    /** Match worlds (by key namespace): client time and same-shape material swaps. */
    public record Games(Set<String> namespaces, long playerTime, Set<String> paletteNamespaces,
            Map<String, String> palette) {
        public Games {
            namespaces = Set.copyOf(namespaces);
            paletteNamespaces = Set.copyOf(paletteNamespaces);
            palette = Map.copyOf(palette);
        }

        public boolean applies(String namespace) {
            return namespace != null && namespaces.contains(namespace.toLowerCase(Locale.ROOT));
        }

        public boolean appliesPalette(String namespace) {
            return namespace != null && paletteNamespaces.contains(namespace.toLowerCase(Locale.ROOT));
        }
    }

    /** Server-wide texts that are not per-player messages. */
    public record Texts(String scoreboardTitle, String motdLine, int announceMinutes) {
    }

    public ThemeDefinition {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("Bad theme id " + id);
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Theme " + id + " needs a window whose end is after its start");
        }
        buildBattleThemes = List.copyOf(buildBattleThemes == null ? List.of() : buildBattleThemes);
    }

    /** A theme without advent calendar (Halloween). */
    public ThemeDefinition(String id, ZonedDateTime start, ZonedDateTime end, String messagePrefix, Lobby lobby,
            MusicBoxPlan music, HuntPlan hunt, Games games, Texts texts, List<String> buildBattleThemes) {
        this(id, start, end, messagePrefix, lobby, music, hunt, games, texts, buildBattleThemes, null);
    }

    public boolean coversInstant(java.time.Instant instant) {
        return !instant.isBefore(start.toInstant()) && instant.isBefore(end.toInstant());
    }

    public static ThemeDefinition parse(ConfigurationSection yaml) {
        String id = yaml.getString("id");
        ZoneId zone = ZoneId.of(yaml.getString("window.zone", "Europe/Paris"));
        ZonedDateTime start = LocalDateTime.parse(yaml.getString("window.start", "")).atZone(zone);
        ZonedDateTime end = LocalDateTime.parse(yaml.getString("window.end", "")).atZone(zone);
        String prefix = yaml.getString("messages", "theme." + id);

        ConfigurationSection lobbySection = yaml.getConfigurationSection("lobby");
        Lobby lobby = null;
        if (lobbySection != null) {
            lobby = new Lobby(lobbySection.getString("world", "lobby"), lobbySection.getInt("radius", 48),
                    lobbySection.getInt("min-y", 0), lobbySection.getInt("max-y", 64),
                    lobbySection.getLong("player-time", -1),
                    DecorRules.parse(lobbySection.getConfigurationSection("decor")),
                    lobbySection.getInt("bats", 0), lobbySection.getStringList("ambient.sounds"),
                    lobbySection.getInt("ambient.min-seconds", 40), lobbySection.getInt("ambient.max-seconds", 90),
                    anchorParticles(lobbySection), snowfall(lobbySection.getConfigurationSection("snowfall")));
        }

        ConfigurationSection musicSection = yaml.getConfigurationSection("music");
        MusicBoxPlan music = null;
        if (musicSection != null && !musicSection.getStringList("playlist").isEmpty()) {
            // gap-seconds: a fixed pause or [min, max] picked at random after each song.
            List<Integer> gap = musicSection.isList("gap-seconds") ? musicSection.getIntegerList("gap-seconds")
                    : List.of(musicSection.getInt("gap-seconds", 8));
            int minGap = gap.isEmpty() ? 8 : gap.getFirst();
            int maxGap = gap.isEmpty() ? minGap : gap.getLast();
            music = new MusicBoxPlan(musicSection.getString("world", "lobby"), musicSection.getDouble("x"),
                    musicSection.getDouble("y"), musicSection.getDouble("z"), (float) musicSection.getDouble("yaw"),
                    musicSection.getDouble("radius", 48), (float) musicSection.getDouble("max-gain", 0.5),
                    minGap * 20, maxGap * 20, musicSection.getStringList("playlist"), musician(musicSection));
        }

        ConfigurationSection huntSection = yaml.getConfigurationSection("hunt");
        HuntPlan hunt = null;
        if (huntSection != null) {
            String defaultBlock = huntSection.getString("block", "minecraft:jack_o_lantern");
            List<HuntPlan.Spot> spots = new ArrayList<>();
            for (Map<?, ?> spot : huntSection.getMapList("spots")) {
                String block = spot.get("block") == null ? defaultBlock : String.valueOf(spot.get("block"));
                // A facing is only added when the spot names one: a wool present has no facing state.
                if (spot.get("facing") != null && block.indexOf('[') < 0) {
                    block = block + "[facing=" + String.valueOf(spot.get("facing")).toLowerCase(Locale.ROOT) + "]";
                }
                spots.add(new HuntPlan.Spot(String.valueOf(spot.get("id")), number(spot.get("x")),
                        number(spot.get("y")), number(spot.get("z")),
                        String.valueOf(spot.get("real")).toUpperCase(Locale.ROOT), block));
            }
            HuntPlan.Celebration celebration = HuntPlan.Celebration.HALLOWEEN;
            ConfigurationSection found = huntSection.getConfigurationSection("celebration");
            if (found != null) {
                celebration = new HuntPlan.Celebration(found.getString("item", celebration.item()),
                        found.isString("particle") ? ThemeParticles.parse(found.getString("particle"))
                                : celebration.particle(),
                        found.getString("sound", celebration.sound()));
            }
            hunt = new HuntPlan(huntSection.getString("id"), huntSection.getString("world", "lobby"), spots,
                    huntSection.getInt("coins-per-find", 0), huntSection.getInt("completion-coins", 0),
                    huntSection.getString("reward-cosmetic"), huntSection.getString("reward-source"), celebration);
        }

        ConfigurationSection gamesSection = yaml.getConfigurationSection("games");
        Games games = null;
        if (gamesSection != null) {
            Map<String, String> palette = new LinkedHashMap<>();
            ConfigurationSection paletteSection = gamesSection.getConfigurationSection("palette");
            if (paletteSection != null) {
                for (String key : paletteSection.getKeys(false)) {
                    palette.put(key.toUpperCase(Locale.ROOT), paletteSection.getString(key));
                }
            }
            games = new Games(lowercase(gamesSection.getStringList("namespaces")),
                    gamesSection.getLong("player-time", -1),
                    lowercase(gamesSection.getStringList("palette-namespaces")), palette);
        }

        Texts texts = new Texts(yaml.getString("texts.scoreboard-title"), yaml.getString("texts.motd-line"),
                yaml.getInt("texts.announce-minutes", 0));
        return new ThemeDefinition(id, start, end, prefix, lobby, music, hunt, games, texts,
                yaml.getStringList("build-battle-themes"),
                AdventCalendar.parse(yaml.getConfigurationSection("advent"), zone));
    }

    private static List<AnchorParticle> anchorParticles(ConfigurationSection lobby) {
        if (!lobby.contains("anchor-particles")) return HALLOWEEN_ANCHOR_PARTICLES;
        List<AnchorParticle> out = new ArrayList<>();
        for (Map<?, ?> entry : lobby.getMapList("anchor-particles")) {
            Object near = entry.get("near");
            List<String> parts = near instanceof List<?> list ? list.stream().map(String::valueOf).toList()
                    : List.of(String.valueOf(near));
            out.add(new AnchorParticle(parts, ThemeParticles.parse(String.valueOf(entry.get("particle"))),
                    decimal(entry.get("dy"), 0.9), decimal(entry.get("spread-xz"), 0.15),
                    decimal(entry.get("spread-y"), 0.1), decimal(entry.get("speed"), 0.01)));
        }
        return out;
    }

    private static Snowfall snowfall(ConfigurationSection section) {
        if (section == null) return null;
        List<Double> height = section.isList("height") ? section.getDoubleList("height") : List.of(3.0, 10.0);
        return new Snowfall(ThemeParticles.parse(section.getString("particle", "SNOWFLAKE")),
                section.getInt("count", 6), section.getDouble("radius", 10),
                height.isEmpty() ? 3 : height.getFirst(), height.isEmpty() ? 10 : height.getLast());
    }

    private static Musician musician(ConfigurationSection music) {
        Musician fallback = Musician.HALLOWEEN;
        if (!music.contains("musician") && !music.contains("particle")) return fallback;
        String type = music.getString("musician", fallback.type());
        boolean witch = Musician.WITCH.equalsIgnoreCase(type.trim());
        return new Musician(type,
                music.isString("particle") ? ThemeParticles.parse(music.getString("particle"))
                        : witch ? fallback.particle() : Particle.NOTE,
                music.getString("name-key", witch ? fallback.nameKey() : type.trim().toLowerCase(Locale.ROOT)),
                music.getString("name-color", fallback.nameColor()));
    }

    private static double decimal(Object value, double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(String.valueOf(value).trim());
    }

    private static Set<String> lowercase(List<String> values) {
        return new java.util.HashSet<>(values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList());
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value).trim());
    }
}
