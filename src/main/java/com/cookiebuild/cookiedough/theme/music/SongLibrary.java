package com.cookiebuild.cookiedough.theme.music;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads a theme's songs. Each playlist id is read from
 * {@code plugins/CookieDough/themes/<theme>/music/<id>.yml} or {@code .nbs}
 * when an operator added or replaced it, otherwise from the copy bundled in
 * the JAR. Any other {@code .yml} or {@code .nbs} (Note Block Studio) file
 * dropped in that folder joins the rotation too: {@code /theme reload}
 * picks it up without a restart.
 */
public final class SongLibrary {
    private SongLibrary() {
    }

    public static List<Song> load(JavaPlugin plugin, String themeId, List<String> songIds) {
        Logger logger = plugin.getLogger();
        File folder = new File(plugin.getDataFolder(), "themes/" + themeId + "/music");
        Set<String> ids = new LinkedHashSet<>(songIds);
        ids.addAll(extraSongIds(folder));
        List<Song> songs = new ArrayList<>();
        for (String songId : ids) {
            try {
                Song song = loadOne(plugin, themeId, folder, songId);
                if (song == null) {
                    logger.warning("Theme " + themeId + ": song " + songId + " not found");
                } else {
                    songs.add(song);
                }
            } catch (Exception error) {
                logger.warning("Theme " + themeId + ": song " + songId + " skipped: " + error.getMessage());
            }
        }
        return songs;
    }

    /** Song files an operator dropped in the theme's music folder, by id (file name without extension). */
    static List<String> extraSongIds(File folder) {
        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".yml") || lower.endsWith(".nbs");
        });
        if (files == null) return List.of();
        return Arrays.stream(files).map(File::getName).sorted()
                .map(name -> name.substring(0, name.lastIndexOf('.'))).distinct().toList();
    }

    private static Song loadOne(JavaPlugin plugin, String themeId, File folder, String songId) throws Exception {
        File nbs = new File(folder, songId + ".nbs");
        if (nbs.isFile()) return NbsParser.parse(songId, Files.readAllBytes(nbs.toPath()));
        File yml = new File(folder, songId + ".yml");
        if (yml.isFile()) {
            return SongParser.parse(songId, new StringReader(Files.readString(yml.toPath(), StandardCharsets.UTF_8)));
        }
        String base = "themes/" + themeId + "/music/" + songId;
        try (InputStream stream = plugin.getResource(base + ".nbs")) {
            if (stream != null) return NbsParser.parse(songId, stream.readAllBytes());
        }
        try (InputStream stream = plugin.getResource(base + ".yml")) {
            if (stream == null) return null;
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return SongParser.parse(songId, reader);
            }
        }
    }
}
