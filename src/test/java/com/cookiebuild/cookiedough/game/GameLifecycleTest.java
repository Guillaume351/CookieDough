package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;

class GameLifecycleTest {
    @Test
    void removingAnOnlineParticipantClearsGameOnlyState() throws Exception {
        TestGame game = new TestGame("Pitchout");
        CookiePlayer player = cookiePlayer();
        player.setState(PlayerState.SPECTATING);
        players(game).add(player);

        game.removePlayer(player, "game_cleanup");

        assertEquals(PlayerState.LOBBY, player.getState());
        assertFalse(game.getPlayers().contains(player));
    }

    @Test
    void leavingAQueueRestoresLobbyState() throws Exception {
        TestGame game = new TestGame("SkyWars");
        CookiePlayer player = cookiePlayer();
        player.setState(PlayerState.QUEUED);
        players(game).add(player);

        game.removePlayer(player, "left_queue");

        assertEquals(PlayerState.LOBBY, player.getState());
        assertFalse(game.getPlayers().contains(player));
    }

    @Test
    void aRealOrphanStillKeepsItsGameStateForLobbyDiagnostics() {
        CookiePlayer orphan = cookiePlayer();
        orphan.setState(PlayerState.IN_GAME);

        assertEquals(PlayerState.IN_GAME, orphan.getState());
        assertEquals(null, GameManager.getGameOfPlayer(orphan));
    }

    @Test
    void detectsAnAlreadyPreparedSpareArenaForTheSameMode() {
        TestGame current = new TestGame("SkyWars");
        TestGame spare = new TestGame("skywars");
        GameManager.addGame(current);
        try {
            assertFalse(GameManager.hasOtherOpenGame(current));
            GameManager.addGame(spare);
            assertTrue(GameManager.hasOtherOpenGame(current));
        } finally {
            GameManager.removeGame(current);
            GameManager.removeGame(spare);
        }
    }

    @Test
    void countsDistinctOnlinePlayersOwnedByGames() throws Exception {
        TestGame waiting = new TestGame("Pitchout");
        TestGame running = new TestGame("SkyWars");
        CookiePlayer sharedPlayer = cookiePlayer(true);
        CookiePlayer offlinePlayer = cookiePlayer(false);
        players(waiting).add(sharedPlayer);
        players(running).add(sharedPlayer);
        players(running).add(offlinePlayer);
        GameManager.addGame(waiting);
        GameManager.addGame(running);

        try {
            assertEquals(1, GameManager.getOnlineGamePlayerCount());
        } finally {
            GameManager.removeGame(waiting);
            GameManager.removeGame(running);
        }
    }

    @Test
    void countsDistinctOnlinePlayersAcrossEveryArenaOfOneMode() throws Exception {
        TestGame firstPitchoutArena = new TestGame("Pitchout");
        TestGame secondPitchoutArena = new TestGame("pitchout");
        TestGame skyWarsArena = new TestGame("SkyWars");
        CookiePlayer sharedPitchoutPlayer = cookiePlayer(true);
        CookiePlayer secondPitchoutPlayer = cookiePlayer(true);
        CookiePlayer offlineReservation = cookiePlayer(false);
        CookiePlayer skyWarsPlayer = cookiePlayer(true);

        players(firstPitchoutArena).add(sharedPitchoutPlayer);
        players(secondPitchoutArena).add(sharedPitchoutPlayer);
        players(secondPitchoutArena).add(secondPitchoutPlayer);
        players(secondPitchoutArena).add(offlineReservation);
        players(skyWarsArena).add(skyWarsPlayer);
        GameManager.addGame(firstPitchoutArena);
        GameManager.addGame(secondPitchoutArena);
        GameManager.addGame(skyWarsArena);

        try {
            assertEquals(2, GameManager.getOnlineGamePlayerCount("PITCHOUT"));
            assertEquals(1, GameManager.getOnlineGamePlayerCount("skywars"));
            assertEquals(0, GameManager.getOnlineGamePlayerCount("Skyblock"));
            assertEquals(0, GameManager.getOnlineGamePlayerCount(null));
        } finally {
            GameManager.removeGame(firstPitchoutArena);
            GameManager.removeGame(secondPitchoutArena);
            GameManager.removeGame(skyWarsArena);
        }
    }

    @Test
    void defaultAdministrativeShutdownClosesAndUnregistersTheGame() {
        TestGame game = new TestGame("BuildBattles");
        GameManager.addGame(game);

        game.shutdown();

        assertEquals(GameState.FINISHED, game.getState());
        assertFalse(game.isAdmissionsOpen());
        assertFalse(GameManager.getGames().contains(game));
    }

    @Test
    void anIneligibleTeamCompositionNeverAdvancesTheCountdown() throws Exception {
        TestGame game = new TestGame("BedWars");
        game.setCountdownEligible(false);
        players(game).add(cookiePlayer());
        players(game).add(cookiePlayer());

        game.tick();
        game.tick();

        assertEquals(0, game.getStartTimer());
        assertEquals(0, game.startCalls());
    }

    @Test
    void waitingActionBarIsRefreshedEverySecondForEveryPlayer() throws Exception {
        TestGame game = new TestGame("BedWars");
        AtomicInteger firstUpdates = new AtomicInteger();
        AtomicInteger secondUpdates = new AtomicInteger();
        players(game).add(cookiePlayer(true, firstUpdates));
        players(game).add(cookiePlayer(true, secondUpdates));
        game.setCountdownEligible(false);

        game.tick();
        game.tick();

        assertEquals(2, firstUpdates.get());
        assertEquals(2, secondUpdates.get());
    }

    @Test
    void losingCountdownEligibilityImmediatelyResetsTheTimer() throws Exception {
        TestGame game = new TestGame("BedWars");
        players(game).add(cookiePlayer());
        players(game).add(cookiePlayer());

        game.tick();
        assertEquals(1, game.getStartTimer());

        game.setCountdownEligible(false);
        game.tick();

        assertEquals(0, game.getStartTimer());
        assertEquals(0, game.startCalls());
    }

    @SuppressWarnings("unchecked")
    private static List<CookiePlayer> players(Game game) throws Exception {
        Field players = Game.class.getDeclaredField("players");
        players.setAccessible(true);
        return (List<CookiePlayer>) players.get(game);
    }

    private static CookiePlayer cookiePlayer() {
        return cookiePlayer(true);
    }

    private static CookiePlayer cookiePlayer(boolean online) {
        return cookiePlayer(online, new AtomicInteger());
    }

    private static CookiePlayer cookiePlayer(boolean online, AtomicInteger actionBarUpdates) {
        UUID id = UUID.randomUUID();
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] { Player.class }, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getName" -> "TestPlayer";
                        case "isOnline" -> online;
                        case "sendActionBar" -> {
                            actionBarUpdates.incrementAndGet();
                            yield null;
                        }
                        default -> defaultValue(method.getReturnType());
                    };
                });
        return new CookiePlayer(player);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private static final class TestGame extends Game {
        private boolean countdownEligible = true;
        private int startCalls;

        private TestGame(String name) {
            super(name);
        }

        private void setCountdownEligible(boolean countdownEligible) {
            this.countdownEligible = countdownEligible;
        }

        private int startCalls() {
            return startCalls;
        }

        @Override protected boolean canStartCountdown() {
            return countdownEligible && super.canStartCountdown();
        }
        @Override public void startGame() {
            startCalls++;
            super.startGame();
        }
        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(CookiePlayer player) { }
        @Override public boolean isGameEnded() { return false; }
    }
}
