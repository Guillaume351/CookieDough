package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.ui.BedrockFormImages;

class FunnelMenuModelsTest {
    private static final List<Locale> LOCALES = List.of(Locale.ENGLISH, Locale.FRENCH, Locale.of("es"),
            Locale.GERMAN, Locale.ITALIAN, Locale.of("pt", "BR"), Locale.of("bg"), Locale.of("hi"));

    @Test
    void postMatchChoiceReplaysTheSameModeFirstAndKeepsFeedbackSecondary() {
        HubGameMenuModel model = FunnelMenuModels.replay("SkyWars", Locale.FRENCH, null, false);
        List<String> actions = model.entries().stream().map(HubGameMenuModel.Entry::action).toList();
        assertEquals(List.of("replay:same", "replay:quick", "replay:auto", "replay:feedback", "replay:lobby"),
                actions);
        assertEquals("Rejouer à Sky Wars", model.entries().getFirst().label());
        assertEquals("Rester au lobby", model.entries().getLast().label());
    }

    @Test
    void postMatchChoiceAdvertisesAnotherQueueThatAlreadyHasPlayers() {
        HubGameMenuModel model = FunnelMenuModels.replay("SkyWars", Locale.ENGLISH,
                new FunnelMenuModels.QueueChoice("BuildBattles", 1), true);
        assertEquals("replay:join:BuildBattles", model.entries().get(1).action());
        assertEquals("Join Build Battle", model.entries().get(1).label());
        assertEquals("Auto replay: on", model.entries().get(3).label());
        assertEquals(5, FunnelMenuModels.replay("SkyWars", Locale.ENGLISH,
                new FunnelMenuModels.QueueChoice("SkyWars", 3), false).entries().size());
    }

    @Test
    void waitingOptionsKeepTheQueueAndOfferSkyblockOnlyAsAWayToWait() {
        HubGameMenuModel model = FunnelMenuModels.waiting("BuildBattles", 2, Locale.FRENCH,
                new FunnelMenuModels.QueueChoice("MicroBattles", 3), true);
        List<String> actions = model.entries().stream().map(HubGameMenuModel.Entry::action).toList();
        assertEquals(List.of("wait:stay", "wait:switch:MicroBattles", "wait:activity:Skyblock",
                "wait:practice", "wait:rally"), actions);
        assertTrue(model.content().contains("Build Battle"));
        assertFalse(FunnelMenuModels.waiting("BuildBattles", 2, Locale.FRENCH, null, false).entries().stream()
                .anyMatch(entry -> entry.action().startsWith("wait:switch") || entry.action().contains("Skyblock")));
    }

    @Test
    void switchOfferIsAnExplicitOneTapChoice() {
        HubGameMenuModel model = FunnelMenuModels.switchOffer("BuildBattles", Locale.ENGLISH,
                new FunnelMenuModels.QueueChoice("Pitchout", 2));
        assertEquals(List.of("wait:switch:Pitchout", "wait:stay"),
                model.entries().stream().map(HubGameMenuModel.Entry::action).toList());
        assertEquals("Yes, join Pitchout", model.entries().getFirst().label());
    }

    @Test
    void communityPageSpellsOutTheInviteForBedrock() {
        HubGameMenuModel model = FunnelMenuModels.community(Locale.FRENCH);
        assertTrue(model.content().contains(FunnelMenuModels.DISCORD_INVITE));
        assertTrue(model.content().contains(FunnelMenuModels.APP_URL));
        assertEquals("onboarding", model.entries().getFirst().action());
    }

    @Test
    void everyLocaleResolvesEveryLabelAndEveryImageExistsInThePack() {
        for (Locale locale : LOCALES) {
            for (HubGameMenuModel model : List.of(
                    FunnelMenuModels.replay("BuildBattles", locale, new FunnelMenuModels.QueueChoice("SkyWars", 1),
                            false),
                    FunnelMenuModels.waiting("BuildBattles", 2, locale,
                            new FunnelMenuModels.QueueChoice("SkyWars", 1), true),
                    FunnelMenuModels.switchOffer("BuildBattles", locale, new FunnelMenuModels.QueueChoice("SkyWars", 1)),
                    FunnelMenuModels.community(locale))) {
                assertFalse(model.title().contains("."), model.title());
                for (HubGameMenuModel.Entry entry : model.entries()) {
                    assertFalse(entry.label().matches("[a-z_]+(\\.[a-z_]+)+"), entry.label());
                    assertTrue(BedrockFormImages.isKnown(entry.bedrockTexture()), entry.bedrockTexture());
                }
            }
        }
    }

    @Test
    void javaFunnelInventoriesCentreUpToSevenChoices() {
        for (int count = 0; count <= 7; count++) {
            List<Integer> slots = PlayerHubMenu.centredSlots(count);
            assertEquals(count, slots.size());
            assertEquals(count, slots.stream().distinct().count());
            assertTrue(slots.stream().allMatch(slot -> slot >= 9 && slot <= 17));
        }
    }
}
