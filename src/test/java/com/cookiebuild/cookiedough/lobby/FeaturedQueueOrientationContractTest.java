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
    void aSoloStartFeaturedModeNeverPromisesASecondPlayer() {
        assertEquals("hub.onboarding.featured_lore_solo",
                PlayerHubMenu.onboardingPrimaryButton(false, true).loreKey());
        assertEquals("hub.onboarding.featured_lore",
                PlayerHubMenu.onboardingPrimaryButton(false, false).loreKey());
        assertEquals("hub.quick.lore", PlayerHubMenu.onboardingPrimaryButton(true, true).loreKey());
        for (java.util.Locale locale : java.util.List.of(java.util.Locale.ENGLISH, java.util.Locale.FRENCH,
                java.util.Locale.GERMAN, java.util.Locale.ITALIAN, java.util.Locale.of("bg"),
                java.util.Locale.of("es"), java.util.Locale.of("hi"), java.util.Locale.of("pt", "BR"))) {
            for (String key : java.util.List.of("hub.onboarding.featured_lore_solo", "lobby.solo.featured_solo",
                    "lobby.solo.featured_solo.bedrock", "hub.games.solo_start", "rally.call.game.launched_solo")) {
                String text = com.cookiebuild.cookiedough.utils.LocaleManager.getMessage(key, locale, "Build Battle");
                for (String secondPlayer : java.util.List.of("2e", "2nd", "2.", "2º", "2°", "2-ри", "दूसरा")) {
                    assertFalse(text.contains(secondPlayer), key + " " + locale + ": " + text);
                }
            }
        }
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
