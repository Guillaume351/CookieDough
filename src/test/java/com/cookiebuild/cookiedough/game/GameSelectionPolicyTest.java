package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    void rotatesEmptyQueuesDeterministically() {
        GameSelectionPolicy policy = new GameSelectionPolicy();
        StubGame build = game("BuildBattles", 12, 2, 0);
        StubGame sky = game("SkyWars", 12, 2, 0);
        StubGame turf = game("TurfWars", 12, 2, 0);
        List<Game> candidates = List.of(turf, sky, build);

        assertSame(build, policy.select(candidates));
        assertSame(sky, policy.select(candidates));
        assertSame(turf, policy.select(candidates));
        assertSame(build, policy.select(candidates));
    }

    @Test
    void keepsConcurrentEmptyQueueSelectionsBalanced() throws Exception {
        GameSelectionPolicy policy = new GameSelectionPolicy();
        List<Game> candidates = List.of(
                game("MicroBattles", 12, 2, 0),
                game("SkyWars", 12, 2, 0),
                game("TurfWars", 12, 2, 0));
        Map<String, AtomicInteger> selections = new ConcurrentHashMap<>();
        int calls = 300;
        int workers = 12;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(workers)) {
            for (int index = 0; index < calls; index++) {
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    Game selected = policy.select(candidates);
                    selections.computeIfAbsent(selected.getGameName(), ignored -> new AtomicInteger())
                            .incrementAndGet();
                    return null;
                });
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertEquals(100, selections.get("MicroBattles").get());
        assertEquals(100, selections.get("SkyWars").get());
        assertEquals(100, selections.get("TurfWars").get());
    }

    private static StubGame game(String name, int capacity, int minimumPlayers, int playerCount) {
        StubGame game = new StubGame(name, playerCount);
        game.setCapacity(capacity);
        game.setMinimumPlayers(minimumPlayers);
        return game;
    }

    private static final class StubGame extends Game {
        private final int playerCount;

        private StubGame(String name, int playerCount) {
            super(name);
            this.playerCount = playerCount;
        }

        @Override public int getPlayerCount() { return playerCount; }
        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(CookiePlayer player) { }
        @Override public boolean isGameEnded() { return false; }
    }
}
