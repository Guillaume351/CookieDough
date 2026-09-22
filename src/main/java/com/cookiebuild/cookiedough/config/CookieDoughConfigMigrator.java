package com.cookiebuild.cookiedough.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Versioned, additive migration policy for CookieDough's persisted YAML. */
public final class CookieDoughConfigMigrator {
    public static final int CURRENT_VERSION = 1;

    public record Result(int previousVersion, int effectiveVersion,
            boolean changed, boolean futureVersion, Set<String> unsupportedKeys) { }

    private CookieDoughConfigMigrator() { }

    public static Result migrate(FileConfiguration live, Configuration persisted, Configuration defaults) {
        if (live == null || persisted == null || defaults == null) {
            throw new IllegalArgumentException("Live, persisted and default configurations are required");
        }
        int previous = persisted.getInt("config-version", 0);
        Set<String> unsupported = new LinkedHashSet<>(leafKeys(persisted));
        unsupported.removeAll(leafKeys(defaults));

        if (previous > CURRENT_VERSION) {
            return new Result(previous, previous, false, true, Set.copyOf(unsupported));
        }

        boolean changed = false;
        if (previous < 1) {
            changed |= copyDefaultIfMissing(live, persisted, defaults,
                    "lobby.champion-heads-enabled");
            changed |= copyDefaultIfMissing(live, persisted, defaults,
                    "lobby.leaderboard-panels-enabled");
            live.set("config-version", 1);
            changed = true;
        }
        return new Result(previous, CURRENT_VERSION, changed, false, Set.copyOf(unsupported));
    }

    private static boolean copyDefaultIfMissing(FileConfiguration live, Configuration persisted,
            Configuration defaults, String path) {
        if (persisted.contains(path, true)) return false;
        live.set(path, defaults.get(path));
        return true;
    }

    private static Set<String> leafKeys(Configuration configuration) {
        Set<String> leaves = new LinkedHashSet<>();
        for (String path : configuration.getKeys(true)) {
            if (!(configuration.get(path) instanceof ConfigurationSection)) leaves.add(path);
        }
        return leaves;
    }
}
