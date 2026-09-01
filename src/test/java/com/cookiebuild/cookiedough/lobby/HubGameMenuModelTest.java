package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.ui.BedrockFormImages;

class HubGameMenuModelTest {
    @Test
    void everyLocaleGetsQuickPlayAndSevenReadableGameSubmenusWithImagesAndSafeActions() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH, Locale.of("es"), Locale.GERMAN,
                Locale.ITALIAN, Locale.of("pt", "BR"), Locale.of("bg"), Locale.of("hi"))) {
            HubGameMenuModel index = HubGameMenuModel.index(locale);
            assertEquals(8, index.entries().size());
            assertEquals("quick", index.entries().getFirst().action());
            assertEquals("actions/quick_play", index.entries().getFirst().bedrockTexture());
            for (HubGameMenuModel.Entry summary : index.entries().subList(1, index.entries().size())) {
                assertTrue(summary.action().startsWith("game:details:"));
                assertTrue(BedrockFormImages.isKnown(summary.bedrockTexture()));
                HubGameMenuModel detail = HubGameMenuModel.detail(
                        summary.action().substring("game:details:".length()), locale);
                assertTrue(detail.entries().get(0).action().startsWith("game:join:")
                        || detail.entries().get(0).action().equals("games"));
                assertEquals(List.of("games", "close"), detail.entries().stream()
                        .skip(detail.entries().size() - 2L).map(HubGameMenuModel.Entry::action).toList());
                assertTrue(detail.content().contains("\n"));
                assertFalse(detail.content().contains("game.rules."));
                assertTrue(detail.entries().stream()
                        .allMatch(entry -> BedrockFormImages.isKnown(entry.bedrockTexture())));
            }
        }
    }

    @Test
    void runningArenaAddsTheSameImageBackedSpectateActionForJavaAndBedrock() {
        Game arena = new SpectatableGame();
        arena.setState(GameState.RUNNING);
        GameManager.addGame(arena);
        try {
            HubGameMenuModel detail = HubGameMenuModel.detail("BedWars", Locale.ENGLISH);
            HubGameMenuModel.Entry entry = detail.entries().stream()
                    .filter(candidate -> candidate.action().equals("game:spectate:BedWars"))
                    .findFirst().orElseThrow();
            assertEquals("actions/preview", entry.bedrockTexture());
            assertEquals(org.bukkit.Material.ENDER_EYE, entry.icon());
        } finally {
            GameManager.removeGame(arena);
        }
    }

    private static final class SpectatableGame extends Game {
        private SpectatableGame() { super("BedWars"); }
        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(CookiePlayer player) { }
        @Override public boolean supportsSpectating() { return true; }
        @Override public boolean isGameEnded() { return false; }
    }
}
