package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

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

    @SuppressWarnings("unchecked")
    private static List<CookiePlayer> players(Game game) throws Exception {
        Field players = Game.class.getDeclaredField("players");
        players.setAccessible(true);
        return (List<CookiePlayer>) players.get(game);
    }

    private static CookiePlayer cookiePlayer() {
        UUID id = UUID.randomUUID();
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] { Player.class }, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getName" -> "TestPlayer";
                        case "isOnline" -> true;
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
        private TestGame(String name) {
            super(name);
        }

        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(CookiePlayer player) { }
        @Override public boolean isGameEnded() { return false; }
    }
}
