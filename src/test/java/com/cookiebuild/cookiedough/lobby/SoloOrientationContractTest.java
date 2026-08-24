package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.bukkit.Material;

import com.cookiebuild.cookiedough.player.PlayerState;

class SoloOrientationContractTest {
    @Test
    void onboardingFallsBackToGamesWhenSkyblockIsUnavailable() {
        assertEquals("quick", PlayerHubMenu.chooseOnboardingPrimaryAction(true, false));
        assertEquals("game:join:Skyblock", PlayerHubMenu.chooseOnboardingPrimaryAction(false, true));
        assertEquals("games", PlayerHubMenu.chooseOnboardingPrimaryAction(false, false));

        PlayerHubMenu.OnboardingPrimaryButton games = PlayerHubMenu.onboardingPrimaryButton("games");
        assertEquals("games", games.action());
        assertEquals(Material.GRASS_BLOCK, games.material());
        assertEquals("hub.games.name", games.nameKey());
        assertEquals("hub.onboarding.games_lore", games.loreKey());
        assertEquals("actions/games", HubActionImages.texture(games.action()).orElseThrow());

        PlayerHubMenu.OnboardingPrimaryButton skyblock =
                PlayerHubMenu.onboardingPrimaryButton("game:join:Skyblock");
        assertEquals("game:join:Skyblock", skyblock.action());
        assertEquals("modes/skyblock", skyblock.texture());
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
        assertTrue(LobbyManager.shouldSuggestSolo(PlayerState.LOBBY, true, false, false));
        assertTrue(!LobbyManager.shouldSuggestSolo(PlayerState.LOBBY, true, false, true));
        assertTrue(!LobbyManager.shouldSuggestSolo(PlayerState.LOBBY, true, true, false));
        assertTrue(!LobbyManager.shouldSuggestSolo(PlayerState.PERSISTENT_MODE, true, false, false));
        assertTrue(lobby.indexOf("shouldSuggestSolo(player.getState()")
                < lobby.indexOf("game.addPlayerToAvailableTeam(player)"));
    }
}
