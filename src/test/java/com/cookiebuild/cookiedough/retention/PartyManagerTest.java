package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.player.CookiePlayer;

class PartyManagerTest {
    @Test
    void routesFourPlayerPartyAwayFromThreePlayerTeamGame() {
        StubGame microBattles = new StubGame("MicroBattles", 12, 3, 5);
        StubGame pitchout = new StubGame("Pitchout", 8, 8, 0);

        PartyManager.PartyGameSelection selection = PartyManager.selectPartyGame(
                List.of(microBattles, pitchout), 4);

        assertSame(pitchout, selection.game());
        assertEquals("", selection.rejectionReason());
    }

    @Test
    void givesSpecificReasonWhenPartyExceedsOnlyGamesTeamCapacity() {
        StubGame microBattles = new StubGame("MicroBattles", 12, 3, 0);

        PartyManager.PartyGameSelection selection = PartyManager.selectPartyGame(
                List.of(microBattles), 4);

        assertNull(selection.game());
        assertEquals("MicroBattles supports parties of up to 3 players; your party has 4.",
                selection.rejectionReason());
    }

    @Test
    void keepsRoutingCompatiblePartyToMostPopulatedGame() {
        StubGame microBattles = new StubGame("MicroBattles", 12, 3, 2);
        StubGame pitchout = new StubGame("Pitchout", 8, 8, 1);

        PartyManager.PartyGameSelection selection = PartyManager.selectPartyGame(
                List.of(microBattles, pitchout), 3);

        assertSame(microBattles, selection.game());
    }

    @Test
    void rejectsGameWithoutEnoughRemainingMatchCapacity() {
        StubGame pitchout = new StubGame("Pitchout", 8, 8, 6);

        PartyManager.PartyGameSelection selection = PartyManager.selectPartyGame(
                List.of(pitchout), 3);

        assertNull(selection.game());
        assertEquals("Pitchout does not currently have room for all 3 party members.",
                selection.rejectionReason());
    }

    @Test
    void concentratesCompatibleEmptyPartyQueuesOnOneStableModeInsteadOfRotating() {
        // Rotation spread a tiny population across empty queues; parties now use
        // the same featured/stable order as solo Quick Play (audit 2026-09-30).
        StubGame skyWars = new StubGame("SkyWars", 8, 8, 0);
        StubGame turfWars = new StubGame("TurfWars", 8, 8, 0);
        StubGame buildBattles = new StubGame("BuildBattles", 8, 8, 0);

        PartyManager.PartyGameSelection first = PartyManager.selectPartyGame(
                List.of(turfWars, skyWars), 2);
        PartyManager.PartyGameSelection second = PartyManager.selectPartyGame(
                List.of(turfWars, skyWars), 2);

        assertSame(skyWars, first.game());
        assertSame(skyWars, second.game());
        assertSame(buildBattles, PartyManager.selectPartyGame(
                List.of(turfWars, skyWars, buildBattles), 2).game());
    }

    private static final class StubGame extends Game {
        private final int maxPartySize;
        private final int playerCount;

        private StubGame(String name, int capacity, int maxPartySize, int playerCount) {
            super(name);
            setCapacity(capacity);
            this.maxPartySize = maxPartySize;
            this.playerCount = playerCount;
        }

        @Override
        public int getMaxAdmissiblePartySize() {
            return maxPartySize;
        }

        @Override
        public int getPlayerCount() {
            return playerCount;
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
