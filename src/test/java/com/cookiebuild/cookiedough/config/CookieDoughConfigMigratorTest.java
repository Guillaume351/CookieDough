package com.cookiebuild.cookiedough.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CookieDoughConfigMigratorTest {
    @Test
    void migratesLegacyConfigAdditivelyAndPreservesOperatorValues() {
        YamlConfiguration defaults = defaults();
        YamlConfiguration persisted = new YamlConfiguration();
        persisted.set("community-event.title", "Friday with players");
        persisted.set("removed.setting", true);
        YamlConfiguration live = new YamlConfiguration();
        for (String key : persisted.getKeys(true)) live.set(key, persisted.get(key));

        CookieDoughConfigMigrator.Result result = CookieDoughConfigMigrator.migrate(
                live, persisted, defaults);

        assertTrue(result.changed());
        assertEquals(0, result.previousVersion());
        assertEquals(1, live.getInt("config-version"));
        assertEquals("Friday with players", live.getString("community-event.title"));
        assertTrue(live.getBoolean("lobby.champion-heads-enabled"));
        assertFalse(live.getBoolean("lobby.leaderboard-panels-enabled"));
        assertEquals(java.util.Set.of("removed.setting"), result.unsupportedKeys());
    }

    @Test
    void neverDowngradesAConfigWrittenByANewerPlugin() {
        YamlConfiguration defaults = defaults();
        YamlConfiguration persisted = new YamlConfiguration();
        persisted.set("config-version", 7);
        YamlConfiguration live = new YamlConfiguration();
        live.set("config-version", 7);

        CookieDoughConfigMigrator.Result result = CookieDoughConfigMigrator.migrate(
                live, persisted, defaults);

        assertTrue(result.futureVersion());
        assertFalse(result.changed());
        assertEquals(7, live.getInt("config-version"));
    }

    private static YamlConfiguration defaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("config-version", 1);
        defaults.set("community-event.title", "Cookie Build Community Session");
        defaults.set("lobby.champion-heads-enabled", true);
        defaults.set("lobby.leaderboard-panels-enabled", false);
        return defaults;
    }
}
