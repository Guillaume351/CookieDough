package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.player.CookiePlayer;

class GameConfigurationTest {
    @Test
    void supportsModeSpecificMinimumPlayerCounts() {
        TestGame game = new TestGame();
        game.setCapacity(12);
        game.setMinimumPlayers(4);

        assertEquals(12, game.getCapacity());
        assertEquals(4, game.getMinimumPlayers());
        assertThrows(IllegalArgumentException.class, () -> game.setMinimumPlayers(13));
        assertThrows(IllegalArgumentException.class, () -> game.setCapacity(3));
    }

    @Test
    void admissionsCanOnlyReopenWhileGameIsOpen() {
        TestGame game = new TestGame();
        assertTrue(game.isAdmissionsOpen());
        assertEquals(0, game.getQueuePlayerCount());

        game.closeAdmissions();
        assertFalse(game.isAdmissionsOpen());
        assertEquals(0, game.getQueuePlayerCount());
        assertTrue(game.reopenAdmissions());

        game.setState(GameState.RUNNING);
        assertFalse(game.reopenAdmissions());
        assertFalse(game.isAdmissionsOpen());
    }

    @Test
    void exposesTheRemainingCountdownForLobbyDisplays() {
        TestGame game = new TestGame();
        assertEquals(0, game.getCountdownSeconds());

        game.setStartTimer(21);
        assertEquals(9, game.getCountdownSeconds());
    }

    private static final class TestGame extends Game {
        private TestGame() {
            super("Test");
        }

        @Override
        public void registerANewGame() {
        }

        @Override
        protected void teleportToGame(CookiePlayer player) {
        }

        @Override
        public boolean isGameEnded() {
            return false;
        }
    }
}
