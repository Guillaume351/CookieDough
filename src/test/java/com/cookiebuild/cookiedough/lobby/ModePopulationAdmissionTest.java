package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.player.CookiePlayer;

class ModePopulationAdmissionTest {
    @Test
    void soloReadinessRejectsClosedAdmissionsAndFullArenas() {
        StubGame ready = new StubGame(2, 1);
        assertTrue(ModePopulationService.hasReadyMatchForOneMorePlayer(List.of(ready)));

        ready.closeAdmissions();
        assertFalse(ModePopulationService.hasReadyMatchForOneMorePlayer(List.of(ready)));
        assertFalse(ModePopulationService.hasReadyMatchForOneMorePlayer(List.of(new StubGame(2, 2))));
    }

    private static final class StubGame extends Game {
        private final int playerCount;

        private StubGame(int capacity, int playerCount) {
            super("BedWars");
            setCapacity(capacity);
            setMinimumPlayers(2);
            this.playerCount = playerCount;
        }

        @Override public int getPlayerCount() { return playerCount; }
        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(CookiePlayer player) { }
        @Override public boolean isGameEnded() { return false; }
    }
}
