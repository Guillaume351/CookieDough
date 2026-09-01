package com.cookiebuild.cookiedough.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;

class ActivityQueueIntentTest {
    @AfterEach
    void clearActivities() {
        ActivityRegistry.clearForTests();
    }

    @Test
    void registeringAPassiveQueueIntentPromptsItsOwningActivity() {
        UUID playerId = UUID.randomUUID();
        CookiePlayer player = cookiePlayer(playerId);
        player.setState(PlayerState.PERSISTENT_MODE);
        AtomicInteger prompts = new AtomicInteger();
        PersistentActivity activity = activity(playerId, prompts);
        TestGame game = new TestGame();
        ActivityRegistry.register(activity);
        GameManager.addGame(game);

        try {
            assertTrue(GameManager.registerQueueIntent(player, game));
            assertEquals(1, prompts.get());
            assertEquals(game.getGameId(), GameManager.getQueueIntent(playerId).gameId());
        } finally {
            GameManager.cancelQueueIntent(playerId);
            GameManager.removeGame(game);
            ActivityRegistry.unregister(activity);
        }
    }

    @Test
    void anExpectedLeaveRefusalUsesTheActivityRecoveryHook() {
        UUID playerId = UUID.randomUUID();
        CookiePlayer player = cookiePlayer(playerId);
        AtomicInteger prompts = new AtomicInteger();
        PersistentActivity activity = activity(playerId, prompts);
        ActivityRegistry.register(activity);

        ActivityRegistry.notifyLeaveBlocked(player, "returned_lobby");

        assertEquals(1, prompts.get());
    }

    private static PersistentActivity activity(UUID playerId, AtomicInteger prompts) {
        return new PersistentActivity() {
            @Override public String name() { return "TestActivity"; }
            @Override public boolean isAvailable() { return true; }
            @Override public ActivityAdmissionResult enter(CookiePlayer player) {
                return ActivityAdmissionResult.admitted("ready");
            }
            @Override public boolean leave(CookiePlayer player, String reason) { return true; }
            @Override public boolean owns(UUID candidate) { return playerId.equals(candidate); }
            @Override public void onLeaveBlocked(CookiePlayer player, String reason) { prompts.incrementAndGet(); }
            @Override public void onQueueIntentRegistered(CookiePlayer player) { prompts.incrementAndGet(); }
        };
    }

    private static CookiePlayer cookiePlayer(UUID playerId) {
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] { Player.class }, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "getUniqueId" -> playerId;
                            case "getName" -> "QueuedPlayer";
                            case "isOnline" -> true;
                            default -> defaultValue(method.getReturnType());
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
        private TestGame() { super("BuildBattles"); }
        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(CookiePlayer player) { }
        @Override public boolean isGameEnded() { return false; }
    }
}
