package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class HubActionImagesTest {
    @Test
    void firstLobbyPagesUseExplicitPackPathsWithUnknownActionFallback() {
        Map<String, String> expected = Map.of(
                "quick", "actions/quick_play",
                "games", "actions/games",
                "goals", "actions/goals",
                "friends", "actions/friends",
                "party", "actions/party",
                "events", "actions/events",
                "app", "actions/app",
                "help", "actions/help");
        expected.forEach((action, texture) -> assertEquals(
                texture, HubActionImages.texture(action).orElseThrow()));
        assertEquals("actions/friends", HubActionImages.texture("community").orElseThrow());
        assertEquals("actions/back", HubActionImages.texture("back").orElseThrow());
        assertTrue(HubActionImages.texture("unknown").isEmpty());
    }
}
