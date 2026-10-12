package com.cookiebuild.cookiedough.theme.music;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads a theme's songs: {@code plugins/CookieDough/themes/<theme>/music/<song>.yml}
 * when an operator added or replaced one, otherwise the copy bundled in the JAR.
 */
public final class SongLibrary {
    private SongLibrary() {
    }

    public static List<Song> load(JavaPlugin plugin, String themeId, List<String> songIds) {
        Logger logger = plugin.getLogger();
        List<Song> songs = new ArrayList<>();
        for (String songId : songIds) {
            String path = "themes/" + themeId + "/music/" + songId + ".yml";
            File override = new File(plugin.getDataFolder(), path);
            try (Reader reader = override.isFile()
                    ? Files.newBufferedReader(override.toPath(), StandardCharsets.UTF_8)
                    : bundled(plugin, path)) {
                if (reader == null) {
                    logger.warning("Theme " + themeId + ": song " + songId + " not found");
                    continue;
                }
                songs.add(SongParser.parse(songId, reader));
            } catch (Exception error) {
                logger.warning("Theme " + themeId + ": song " + songId + " skipped: " + error.getMessage());
            }
        }
        return songs;
    }

    private static Reader bundled(JavaPlugin plugin, String path) {
        InputStream stream = plugin.getResource(path);
        return stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
