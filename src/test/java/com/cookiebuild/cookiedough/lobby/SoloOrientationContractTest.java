package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SoloOrientationContractTest {
    @Test
    void onboardingFallsBackToGamesWhenSkyblockIsUnavailable() {
        assertEquals("quick", PlayerHubMenu.chooseOnboardingPrimaryAction(true, false));
        assertEquals("game:join:Skyblock", PlayerHubMenu.chooseOnboardingPrimaryAction(false, true));
        assertEquals("games", PlayerHubMenu.chooseOnboardingPrimaryAction(false, false));
    }

    @Test
    void habitualQuickPlayOpensSharedNonForcingSoloChoice() throws Exception {
        String lobby = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/LobbyManager.java"));
        String menu = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/PlayerHubMenu.java"));
        assertTrue(lobby.contains("ModePopulationService.isPersistentActivityAvailable(\"Skyblock\")"));
        assertTrue(lobby.contains("getPartyId("));
        assertTrue(lobby.contains("openSoloSuggestion(player.getPlayer())"));
        assertTrue(menu.contains("case SOLO -> soloInventory(player)"));
        assertTrue(menu.contains("case SOLO -> {"));
        assertTrue(menu.contains("\"game:join:Skyblock\", \"modes/skyblock\""));
    }
}
