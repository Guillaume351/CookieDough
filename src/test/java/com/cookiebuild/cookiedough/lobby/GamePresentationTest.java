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
        assertEquals(7, GamePresentation.games().size());
        for (GamePresentation game : GamePresentation.games()) {
            String description = game.description(Locale.ENGLISH);
            assertFalse(description.isBlank());
            assertNotEquals(game.descriptionKey(), description);
            assertTrue(description.length() <= 100, game.gameName() + " description should stay compact");
        }
    }

    @Test
    void selectorsUseDistinctModelsAndTurfWarsDoesNotUseASunBurningMob() {
        assertEquals(7, new HashSet<>(GamePresentation.games().stream()
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
    void betaModesAreExplicitAndNoModeIsComingSoon() {
        assertEquals(GamePresentation.ReleaseStage.BETA,
                GamePresentation.forGame("BedWars").releaseStage());
        assertEquals("BedWars [BETA]", GamePresentation.forGame("BedWars").displayName(Locale.ENGLISH));
        assertEquals(java.util.Set.of("BedWars", "Skyblock"), GamePresentation.games().stream()
                .filter(game -> game.releaseStage() == GamePresentation.ReleaseStage.BETA)
                .map(GamePresentation::gameName).collect(java.util.stream.Collectors.toSet()));
        assertTrue(GamePresentation.games().stream()
                .noneMatch(game -> game.releaseStage() == GamePresentation.ReleaseStage.COMING_SOON));
    }
}
