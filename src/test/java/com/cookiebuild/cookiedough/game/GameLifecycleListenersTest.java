package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.player.CookiePlayer;

class GameLifecycleListenersTest {
    @Test
    void adminAndGameplayObserversCanCoexistAndUnregisterIndependently() {
        AtomicInteger adminEvents = new AtomicInteger();
        AtomicInteger gameplayEvents = new AtomicInteger();
        GameManager.GameLifecycleListener admin = event -> adminEvents.incrementAndGet();
        GameManager.GameLifecycleListener gameplay = event -> gameplayEvents.incrementAndGet();
        Game game = new StubGame();

        try {
            GameManager.setLifecycleListener(admin);
            GameManager.setLifecycleListener(gameplay);
            GameManager.setLifecycleListener(admin);
            GameManager.notifyGameChanged(game, "updated");

            assertEquals(1, adminEvents.get());
            assertEquals(1, gameplayEvents.get());

            GameManager.clearLifecycleListener(gameplay);
            GameManager.notifyGameChanged(game, "updated_again");
            assertEquals(2, adminEvents.get());
            assertEquals(1, gameplayEvents.get());
        } finally {
            GameManager.clearLifecycleListener(admin);
            GameManager.clearLifecycleListener(gameplay);
        }
    }

    private static final class StubGame extends Game {
        private StubGame() { super("LifecycleTest"); }
        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(CookiePlayer player) { }
        @Override public boolean isGameEnded() { return false; }
    }
}
