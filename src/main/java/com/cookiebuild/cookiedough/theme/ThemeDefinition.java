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

import org.bukkit.configuration.ConfigurationSection;

import com.cookiebuild.cookiedough.theme.decor.DecorRules;
import com.cookiebuild.cookiedough.theme.hunt.HuntPlan;
import com.cookiebuild.cookiedough.theme.music.MusicBoxPlan;

/**
 * A parsed {@code themes/<id>/theme.yml}. Everything a season changes is data
 * here; the code only knows how to show it without touching any world.
 *
 * @param messagePrefix prefix of this theme's keys in the message bundles
 *        (e.g. {@code theme.halloween} for {@code theme.halloween.tab.header})
 */
public record ThemeDefinition(String id, ZonedDateTime start, ZonedDateTime end, String messagePrefix,
        Lobby lobby, MusicBoxPlan music, HuntPlan hunt, Games games, Texts texts, List<String> buildBattleThemes) {

    /** Lobby ambience: client time, decor area and rules, bats and ambient sounds. */
    public record Lobby(String world, int radius, int minY, int maxY, long playerTime, DecorRules decor,
            int bats, List<String> ambientSounds, int ambientMinSeconds, int ambientMaxSeconds) {
        public Lobby {
            ambientSounds = List.copyOf(ambientSounds);
            if (radius < 1 || radius > 256 || minY > maxY) throw new IllegalArgumentException("Bad lobby decor area");
            if (bats < 0 || bats > 32) throw new IllegalArgumentException("bats must be within 0..32");
            if (ambientMinSeconds < 5 || ambientMaxSeconds < ambientMinSeconds) {
                throw new IllegalArgumentException("Bad ambient sound interval");
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
                    lobbySection.getInt("ambient.min-seconds", 40), lobbySection.getInt("ambient.max-seconds", 90));
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
                    minGap * 20, maxGap * 20, musicSection.getStringList("playlist"));
        }

        ConfigurationSection huntSection = yaml.getConfigurationSection("hunt");
        HuntPlan hunt = null;
        if (huntSection != null) {
            String defaultBlock = huntSection.getString("block", "minecraft:jack_o_lantern");
            List<HuntPlan.Spot> spots = new ArrayList<>();
            for (Map<?, ?> spot : huntSection.getMapList("spots")) {
                String facing = spot.get("facing") == null ? "north"
                        : String.valueOf(spot.get("facing")).toLowerCase(Locale.ROOT);
                String block = spot.get("block") == null ? defaultBlock : String.valueOf(spot.get("block"));
                if (block.indexOf('[') < 0) block = block + "[facing=" + facing + "]";
                spots.add(new HuntPlan.Spot(String.valueOf(spot.get("id")), number(spot.get("x")),
                        number(spot.get("y")), number(spot.get("z")),
                        String.valueOf(spot.get("real")).toUpperCase(Locale.ROOT), block));
            }
            hunt = new HuntPlan(huntSection.getString("id"), huntSection.getString("world", "lobby"), spots,
                    huntSection.getInt("coins-per-find", 0), huntSection.getInt("completion-coins", 0),
                    huntSection.getString("reward-cosmetic"));
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
                yaml.getStringList("build-battle-themes"));
    }

    private static Set<String> lowercase(List<String> values) {
        return new java.util.HashSet<>(values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList());
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value).trim());
    }
}
