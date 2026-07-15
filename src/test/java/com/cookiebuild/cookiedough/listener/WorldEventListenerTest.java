package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldEventListenerTest {
    @Test
    void onlyTheLobbyUsesFrozenPhysics() {
        assertTrue(WorldPolicy.usesFrozenPhysics("lobby"));
        assertTrue(WorldPolicy.usesFrozenPhysics("LOBBY"));
        assertFalse(WorldPolicy.usesFrozenPhysics("skywars_match_123"));
        assertFalse(WorldPolicy.usesFrozenPhysics("microbattles_match_123"));
        assertFalse(WorldPolicy.usesFrozenPhysics(null));
    }

    @Test
    void lobbyBlocksHostilesWithoutBlockingCustomNpcSpawns() {
        assertTrue(WorldPolicy.blocksHostileSpawn("lobby", true, false));
        assertFalse(WorldPolicy.blocksHostileSpawn("lobby", true, true));
        assertFalse(WorldPolicy.blocksHostileSpawn("lobby", false, false));
        assertFalse(WorldPolicy.blocksHostileSpawn("skywars_match_123", true, false));
    }
}
