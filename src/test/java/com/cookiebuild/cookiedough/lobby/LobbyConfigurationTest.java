package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LobbyConfigurationTest {
    @Test
    void shipsCompactChampionHeadsWithoutLeaderboardPanels() {
        YamlConfiguration config = loadBundledConfig();
        assertTrue(config.getBoolean("lobby.champion-heads-enabled", false));
        assertFalse(config.getBoolean("lobby.leaderboard-panels-enabled", true));
        assertTrue(config.getBoolean("lobby.skyblock-billboard.enabled"));
        assertEquals(-1, config.getInt("lobby.skyblock-billboard.map-id"));
        assertEquals("SOUTH", config.getString("lobby.skyblock-billboard.facing"));

        ConfigurationSection selectors = config.getConfigurationSection("lobby.game-selectors");
        assertNotNull(selectors);
        assertEquals(GamePresentation.games().size(), selectors.getKeys(false).size());

        ConfigurationSection skyblock = selectors.getConfigurationSection("skyblock");
        assertNotNull(skyblock);
        assertTrue(skyblock.getBoolean("enabled"));
        assertEquals("Skyblock", skyblock.getString("game"));
        assertEquals("lobby", skyblock.getString("world"));
        assertEquals(8.5, skyblock.getDouble("x"));
        assertEquals(8.0, skyblock.getDouble("y"));
        assertEquals(-8.5, skyblock.getDouble("z"));

        Set<String> occupiedBlocks = new HashSet<>();
        for (String key : selectors.getKeys(false)) {
            ConfigurationSection selector = selectors.getConfigurationSection(key);
            assertNotNull(selector);
            String block = selector.getInt("x") + ":" + selector.getInt("y") + ":" + selector.getInt("z");
            assertTrue(occupiedBlocks.add(block), "Lobby selectors must not overlap: " + block);
        }
    }

    private static YamlConfiguration loadBundledConfig() {
        var stream = LobbyConfigurationTest.class.getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
