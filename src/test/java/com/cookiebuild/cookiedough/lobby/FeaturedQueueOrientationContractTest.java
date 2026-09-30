package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** A lone player is queued into the featured mode, never sent away to a solo beta mode. */
class FeaturedQueueOrientationContractTest {
    @Test
    void onboardingPrimaryIsAlwaysQuickPlayAndShowsTheFeaturedModeWhenNothingIsReady() {
        PlayerHubMenu.OnboardingPrimaryButton ready = PlayerHubMenu.onboardingPrimaryButton(true);
        assertEquals("quick", ready.action());
        assertEquals("hub.quick.name", ready.nameKey());

        PlayerHubMenu.OnboardingPrimaryButton featured = PlayerHubMenu.onboardingPrimaryButton(false);
        assertEquals("quick", featured.action());
        assertEquals(Material.CRAFTING_TABLE, featured.material());
        assertEquals("hub.onboarding.featured_name", featured.nameKey());
        assertEquals("modes/buildbattles", featured.texture());
    }

    @Test
    void quickPlayQueuesFirstThenOffersWaitingActivities() throws Exception {
        String lobby = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/LobbyManager.java"));
        assertFalse(lobby.contains("shouldSuggestSolo"));
        assertFalse(lobby.contains("openSoloSuggestion"));
        assertTrue(lobby.indexOf("game.addPlayerToAvailableTeam(player)")
                < lobby.indexOf("offerWhileWaiting(player.getPlayer()"));
        assertTrue(lobby.contains("GameManager.registerQueueIntent(cookiePlayer, target)"));

        assertTrue(LobbyManager.shouldOfferWhileWaiting(1, 2, false, false));
        assertFalse(LobbyManager.shouldOfferWhileWaiting(2, 2, false, false));
        assertFalse(LobbyManager.shouldOfferWhileWaiting(1, 2, true, false));
        assertFalse(LobbyManager.shouldOfferWhileWaiting(0, 2, false, false));
        // A solo-start mode (e.g. Build Battle after 15 s) starts by itself: no offer to leave.
        assertFalse(LobbyManager.shouldOfferWhileWaiting(1, 2, false, true));
    }
}
