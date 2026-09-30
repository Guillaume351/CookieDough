package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.player.CookiePlayer;

class GameSelectionPolicyTest {
    @Test
    void alwaysConcentratesPlayersInANonEmptyQueueBeforeOpeningAnotherMode() {
        GameSelectionPolicy policy = new GameSelectionPolicy();
        StubGame queued = game("MicroBattles", 12, 4, 1);
        StubGame empty = game("TurfWars", 12, 2, 0);

        assertSame(queued, policy.select(List.of(empty, queued)));
    }

    @Test
    void choosesTheNonEmptyQueueClosestToStarting() {
        GameSelectionPolicy policy = new GameSelectionPolicy();
        StubGame nearlyReady = game("TurfWars", 12, 4, 3);
        StubGame busierButFurtherAway = game("MicroBattles", 12, 8, 5);

        assertSame(nearlyReady, policy.select(List.of(busierButFurtherAway, nearlyReady)));
    }

    @Test
    void excludesQueuesClosedByTheAdminAdmissionGate() {
        GameSelectionPolicy policy = new GameSelectionPolicy();
        StubGame closed = game("TurfWars", 12, 2, 1);
        StubGame open = game("MicroBattles", 12, 2, 0);
        closed.closeAdmissions();

        assertSame(open, policy.select(List.of(closed, open)));
    }

    @Test
    void explicitlySelectedBetaQueuesNeverDivertPublicQuickPlay() {
        GameSelectionPolicy policy = new GameSelectionPolicy();
        StubGame preview = game("NomadWars", 8, 2, 1);
        preview.quickPlayEligible = false;
        StubGame open = game("MicroBattles", 12, 2, 0);
        StubGame fatKing = game("FatKing", 8, 4, 3);
        fatKing.quickPlayEligible = false;
        assertSame(open, policy.select(List.of(preview, fatKing, open)));
        org.junit.jupiter.api.Assertions.assertNull(policy.select(List.of(preview, fatKing)));
    }

    @Test
    void emptyServerAlwaysSelectsTheFixedFeaturedModeInsteadOfRotating() {
        GameSelectionPolicy policy = new GameSelectionPolicy();
        StubGame build = game("BuildBattles", 12, 2, 0);
        StubGame sky = game("SkyWars", 12, 2, 0);
        StubGame turf = game("TurfWars", 12, 2, 0);
        List<Game> candidates = List.of(turf, sky, build);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertSame(build, policy.select(candidates));
        }
    }

    @Test
    void featuredFallbackNeverPicksABetaModeWhenAStableModeIsOpen() {
        GameSelectionPolicy policy = new GameSelectionPolicy();
        StubGame bedWars = game("BedWars", 8, 2, 0);
        StubGame micro = game("MicroBattles", 12, 2, 0);
        StubGame fatKing = game("FatKing", 8, 4, 0);
        assertSame(micro, policy.select(List.of(bedWars, fatKing, micro)));
    }

    @Test
    void readyWithOneMoreBeatsTheBusiestQueueWhichBeatsTheFeaturedMode() {
        GameSelectionPolicy policy = new GameSelectionPolicy();
        StubGame featured = game("BuildBattles", 8, 2, 0);
        StubGame busy = game("MicroBattles", 12, 4, 2);
        StubGame nearlyReady = game("SkyWars", 12, 2, 1);
        assertSame(nearlyReady, policy.select(List.of(featured, busy, nearlyReady)));
        assertSame(busy, policy.select(List.of(featured, busy)));
    }

    @Test
    void passivePlayersWaitingInSkyblockCountAsQueuePopulation() {
        GameSelectionPolicy policy = new GameSelectionPolicy();
        StubGame featured = game("BuildBattles", 8, 2, 0);
        StubGame pitchout = game("Pitchout", 16, 2, 0);
        assertSame(pitchout, policy.select(List.of(featured, pitchout),
                game -> game == pitchout ? 1 : 0));
    }

    @Test
    void busiestOtherQueueExcludesTheCurrentModeEmptyAndFullQueues() {
        StubGame current = game("BuildBattles", 8, 2, 1);
        StubGame empty = game("SkyWars", 12, 2, 0);
        StubGame busy = game("MicroBattles", 12, 4, 3);
        StubGame full = game("Pitchout", 2, 2, 2);
        StubGame quiet = game("TurfWars", 12, 2, 1);
        assertSame(busy, GameSelectionPolicy.busiestOtherQueue(
                List.of(current, empty, busy, full, quiet), "BuildBattles", game -> 0));
        org.junit.jupiter.api.Assertions.assertNull(GameSelectionPolicy.busiestOtherQueue(
                List.of(current, empty), "BuildBattles", game -> 0));
        assertSame(empty, GameSelectionPolicy.busiestOtherQueue(
                List.of(current, empty), "BuildBattles", game -> game == empty ? 1 : 0));
    }

    private static StubGame game(String name, int capacity, int minimumPlayers, int playerCount) {
        StubGame game = new StubGame(name, playerCount);
        game.setCapacity(capacity);
        game.setMinimumPlayers(minimumPlayers);
        return game;
    }

    private static final class StubGame extends Game {
        private final int playerCount;
        private boolean quickPlayEligible = true;

        private StubGame(String name, int playerCount) {
            super(name);
            this.playerCount = playerCount;
        }

        @Override public boolean isQuickPlayEligible() { return quickPlayEligible; }
        @Override public int getPlayerCount() { return playerCount; }
        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(CookiePlayer player) { }
        @Override public boolean isGameEnded() { return false; }
    }
}
