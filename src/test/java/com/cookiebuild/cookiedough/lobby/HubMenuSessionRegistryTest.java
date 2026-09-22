package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class HubMenuSessionRegistryTest {
    @Test
    void stalePageResponseCannotReplaceANewerMenu() {
        HubMenuSessionRegistry registry = new HubMenuSessionRegistry();
        UUID player = UUID.randomUUID();
        UUID stale = registry.issue(player, "GAMES:");
        UUID current = registry.issue(player, "GAME_DETAIL:Skyblock");

        assertFalse(registry.consume(player, stale, "GAMES:"));
        assertTrue(registry.consume(player, current, "GAME_DETAIL:Skyblock"));
        assertEquals(0, registry.size());
    }

    @Test
    void closeInvalidatesOnlyTheExactForm() {
        HubMenuSessionRegistry registry = new HubMenuSessionRegistry();
        UUID player = UUID.randomUUID();
        UUID old = registry.issue(player, "QUEUE:SkyWars");
        UUID current = registry.issue(player, "QUEUE:BedWars");

        registry.invalidate(player, old, "QUEUE:SkyWars");

        assertTrue(registry.consume(player, current, "QUEUE:BedWars"));
    }
}
