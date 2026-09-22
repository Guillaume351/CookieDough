package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class QueueIntentCohortTest {
    private static final UUID GAME = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID PARTY = UUID.fromString("00000000-0000-0000-0000-000000000020");

    @Test
    void incompleteOrMixedPartyCanNeverActivatePartially() {
        GameManager.QueueIntent first = intent("1", PARTY, 2);
        GameManager.QueueIntent second = intent("2", PARTY, 2);
        GameManager.QueueIntent wrongParty = intent("3",
                UUID.fromString("00000000-0000-0000-0000-000000000030"), 2);

        assertFalse(GameManager.isCompleteCohort(List.of(first)));
        assertFalse(GameManager.isCompleteCohort(List.of(first, wrongParty)));
        assertTrue(GameManager.isCompleteCohort(List.of(first, second)));
    }

    @Test
    void waitingIntentSurvivesUntilACompatibleReplacementIsRegistered() {
        GameManager.QueueIntent intent = intent("1", PARTY, 1);
        StubGame replacement = new StubGame("BedWars");
        StubGame stillOpen = new StubGame("BedWars");

        assertTrue(GameManager.shouldAdoptWaitingIntent(intent, replacement, null));
        assertFalse(GameManager.shouldAdoptWaitingIntent(intent, replacement, stillOpen));
        stillOpen.closeAdmissions();
        assertTrue(GameManager.shouldAdoptWaitingIntent(intent, replacement, stillOpen));
        assertFalse(GameManager.shouldAdoptWaitingIntent(intent, new StubGame("SkyWars"), null));
    }

    private static GameManager.QueueIntent intent(String suffix, UUID cohort, int size) {
        UUID player = UUID.fromString("00000000-0000-0000-0000-00000000000" + suffix);
        return new GameManager.QueueIntent(player, GAME, "BedWars", 1L, cohort, size);
    }

    private static final class StubGame extends Game {
        private StubGame(String name) { super(name); }
        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(com.cookiebuild.cookiedough.player.CookiePlayer player) { }
        @Override public boolean isGameEnded() { return false; }
    }
}
