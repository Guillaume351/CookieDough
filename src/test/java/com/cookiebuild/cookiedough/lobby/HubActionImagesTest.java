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
        Map.of(
                "queue:leave", "actions/close",
                "queue:switch", "actions/games",
                "queue:practice", "actions/preview",
                "queue:rally", "actions/friends",
                "replay:same", "actions/join",
                "replay:quick", "actions/quick_play",
                "replay:lobby", "actions/home")
                .forEach((action, texture) -> assertEquals(
                        texture, HubActionImages.texture(action).orElseThrow()));
        assertTrue(HubActionImages.texture("unknown").isEmpty());
    }
}
