package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Locale;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class GamePresentationTest {
    @Test
    void everyGameHasAConciseDiscoverableDescription() {
        assertEquals(9, GamePresentation.games().size());
        for (GamePresentation game : GamePresentation.games()) {
            String description = game.description(Locale.ENGLISH);
            assertFalse(description.isBlank());
            assertNotEquals(game.descriptionKey(), description);
            assertTrue(description.length() <= 100, game.gameName() + " description should stay compact");
        }
    }

    @Test
    void selectorsUseDistinctModelsAndTurfWarsDoesNotUseASunBurningMob() {
        assertEquals(9, new HashSet<>(GamePresentation.games().stream()
                .map(GamePresentation::npcType)
                .toList()).size());

        EntityType turfWars = GamePresentation.forGame("TurfWars").npcType();
        assertEquals(EntityType.SHEEP, turfWars);
        assertFalse(turfWars == EntityType.ZOMBIE
                || turfWars == EntityType.SKELETON
                || turfWars == EntityType.STRAY
                || turfWars == EntityType.DROWNED);
    }

    @Test
    void betaModesAreExplicit() {
        assertEquals(GamePresentation.ReleaseStage.BETA,
                GamePresentation.forGame("BedWars").releaseStage());
        assertEquals("Bed Wars [BETA]", GamePresentation.forGame("BedWars").displayName(Locale.ENGLISH));
        assertEquals(java.util.Set.of("BedWars", "Skyblock", "NomadWars", "FatKing"), GamePresentation.games().stream()
                .filter(game -> game.releaseStage() == GamePresentation.ReleaseStage.BETA)
                .map(GamePresentation::gameName).collect(java.util.stream.Collectors.toSet()));
        assertEquals(java.util.Set.of(), GamePresentation.games().stream()
                .filter(game -> game.releaseStage() == GamePresentation.ReleaseStage.COMING_SOON)
                .map(GamePresentation::gameName).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void playersSeeReadableLocalizedNamesInsteadOfInternalIds() {
        java.util.Map<String, String> expected = java.util.Map.of(
                "BuildBattles", "Build Battle", "FatKing", "Fat King", "NomadWars", "Nomad Wars",
                "BedWars", "Bed Wars", "SkyWars", "Sky Wars", "TurfWars", "Turf Wars",
                "MicroBattles", "Micro Battles", "Pitchout", "Pitchout", "Skyblock", "Skyblock");
        for (Locale locale : java.util.List.of(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN, Locale.ITALIAN,
                Locale.of("es"), Locale.of("pt", "BR"), Locale.of("bg"), Locale.of("hi"))) {
            expected.forEach((id, name) -> assertEquals(name, GamePresentation.readableName(id, locale)));
        }
        assertEquals("Unknown", GamePresentation.readableName("Unknown", Locale.ENGLISH));
    }

    @Test
    void buildBattleIsTheOnlyFeaturedModeAndBetaModesUseRealPackIcons() {
        assertEquals(java.util.Set.of("BuildBattles"), GamePresentation.games().stream()
                .filter(GamePresentation::featured).map(GamePresentation::gameName)
                .collect(java.util.stream.Collectors.toSet()));
        for (GamePresentation game : GamePresentation.games()) {
            assertTrue(com.cookiebuild.cookiedough.ui.BedrockFormImages.isKnown(game.bedrockTexture()));
            assertFalse(game.bedrockTexture().equals("actions/shop")
                    || game.bedrockTexture().equals("actions/preview"), game.gameName());
        }
    }

    @Test
    void onlySkyblockUsesThePersistentActivityNpcRoute() {
        assertEquals(java.util.Set.of("Skyblock"), GamePresentation.games().stream()
                .filter(GamePresentation::persistent)
                .map(GamePresentation::gameName)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(EntityType.BEE, GamePresentation.forGame("Skyblock").npcType());
    }
}
