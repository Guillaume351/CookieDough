package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.game.GameState;

class HubGameMenuOrderingTest {
    @Test
    void featuredModeFirstThenBusiestModesThenCatalogueOrder() {
        Map<String, Integer> population = Map.of("SkyWars", 4, "Pitchout", 1, "BuildBattles", 0);
        List<String> order = HubGameMenuModel.sortedForMenu(GamePresentation.games(),
                game -> population.getOrDefault(game.gameName(), 0)).stream()
                .map(GamePresentation::gameName).toList();
        assertEquals("BuildBattles", order.get(0));
        assertEquals("SkyWars", order.get(1));
        assertEquals("Pitchout", order.get(2));
        assertEquals("MicroBattles", order.get(3));
        assertEquals(9, order.size());
    }

    @Test
    void emptyModesEncourageInsteadOfShowingZeroPlayers() {
        assertEquals("Démarre dès 2 joueurs", HubGameMenuModel.availability(
                new ModePopulationService.Snapshot(0, GameState.OPEN, true, false, 2), Locale.FRENCH));
        assertEquals("Starts with 4 players", HubGameMenuModel.availability(
                new ModePopulationService.Snapshot(0, GameState.OPEN, true, false, 4), Locale.ENGLISH));
        assertEquals("Jouable seul", HubGameMenuModel.availability(
                new ModePopulationService.Snapshot(0, GameState.OPEN, true, true, 1), Locale.FRENCH));
        assertEquals("1 joueur", HubGameMenuModel.availability(
                new ModePopulationService.Snapshot(1, GameState.OPEN, true, false, 2), Locale.FRENCH));
        assertEquals("3 joueurs", HubGameMenuModel.availability(
                new ModePopulationService.Snapshot(3, GameState.OPEN, true, false, 2), Locale.FRENCH));
    }
}
