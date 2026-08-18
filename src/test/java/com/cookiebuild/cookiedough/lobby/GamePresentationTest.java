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
        assertEquals(6, GamePresentation.games().size());
        for (GamePresentation game : GamePresentation.games()) {
            String description = game.description(Locale.ENGLISH);
            assertFalse(description.isBlank());
            assertNotEquals(game.descriptionKey(), description);
            assertTrue(description.length() <= 100, game.gameName() + " description should stay compact");
        }
    }

    @Test
    void selectorsUseDistinctModelsAndTurfWarsDoesNotUseASunBurningMob() {
        assertEquals(6, new HashSet<>(GamePresentation.games().stream()
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
    void bedWarsIsTheOnlyComingSoonGame() {
        assertTrue(GamePresentation.forGame("BedWars").comingSoon());
        assertEquals("BedWars [COMING SOON]", GamePresentation.forGame("BedWars").displayName());
        assertTrue(GamePresentation.games().stream()
                .filter(GamePresentation::comingSoon)
                .allMatch(game -> game.gameName().equals("BedWars")));
    }
}
