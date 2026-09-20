package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.*;

import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class GameAdmissionEligibilityTest {
    @Test
    void rejectedPassiveRequestPreservesExistingIntentAndActivity() throws Exception {
        TestPlayer player = player();
        TestGame allowed = new TestGame(true);
        TestGame preview = new TestGame(false);
        GameManager.QueueIntent original = intent(player, allowed, UUID.randomUUID(), 1);
        intents().put(player.id(), original);
        try {
            assertFalse(GameManager.registerQueueIntent(player.cookie(), preview));
            assertSame(original, GameManager.getQueueIntent(player.id()));
            assertEquals(PlayerState.PERSISTENT_MODE, player.cookie().getState());
            assertEquals(1, player.messages().size());
            assertTrue(player.messages().getFirst().contains("access"));
        } finally {
            GameManager.cancelQueueIntent(player.id());
        }
    }

    @Test
    void deniedPartyCannotRegisterAnyMember() {
        TestPlayer first = player();
        TestPlayer second = player();
        TestGame preview = new TestGame(false);
        assertFalse(GameManager.registerPartyQueueIntent(
                List.of(first.cookie(), second.cookie()), preview, UUID.randomUUID()));
        assertNull(GameManager.getQueueIntent(first.id()));
        assertNull(GameManager.getQueueIntent(second.id()));
        assertEquals(PlayerState.PERSISTENT_MODE, first.cookie().getState());
        assertEquals(PlayerState.PERSISTENT_MODE, second.cookie().getState());
    }

    @Test
    void revokedPermissionCancelsWholePartyExactlyOnceWithoutChangingActivities() throws Exception {
        TestPlayer first = player();
        TestPlayer second = player();
        TestPlayer unrelated = player();
        TestGame preview = new TestGame(true);
        preview.deniedPlayer = first.id();
        UUID cohort = UUID.randomUUID();
        intents().put(first.id(), intent(first, preview, cohort, 2));
        intents().put(second.id(), intent(second, preview, cohort, 2));
        intents().put(unrelated.id(), intent(unrelated, preview, UUID.randomUUID(), 1));
        Map<UUID, Player> online = Map.of(first.id(), first.cookie().getPlayer(),
                second.id(), second.cookie().getPlayer(), unrelated.id(), unrelated.cookie().getPlayer());
        try {
            GameManager.cancelIneligibleQueueIntents(preview, online::get);
            GameManager.cancelIneligibleQueueIntents(preview, online::get);
            assertNull(GameManager.getQueueIntent(first.id()));
            assertNull(GameManager.getQueueIntent(second.id()));
            assertNotNull(GameManager.getQueueIntent(unrelated.id()));
            assertEquals(1, first.messages().size());
            assertEquals(1, second.messages().size());
            assertEquals(0, unrelated.messages().size());
            assertEquals(PlayerState.PERSISTENT_MODE, first.cookie().getState());
            assertEquals(PlayerState.PERSISTENT_MODE, second.cookie().getState());
        } finally {
            online.keySet().forEach(GameManager::cancelQueueIntent);
        }
    }

    @Test
    void finalDirectAdmissionRejectsBeforeAnyPlayerStateMutation() {
        TestPlayer player = player();
        player.cookie().setState(PlayerState.LOBBY);
        TestGame preview = new TestGame(false);
        assertFalse(preview.addPlayer(player.cookie()));
        assertEquals(PlayerState.LOBBY, player.cookie().getState());
        assertEquals(0, preview.getPlayerCount());
    }

    private static GameManager.QueueIntent intent(TestPlayer player, Game game, UUID cohort, int size) {
        return new GameManager.QueueIntent(player.id(), game.getGameId(), game.getGameName(),
                System.currentTimeMillis(), cohort, size);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, GameManager.QueueIntent> intents() throws Exception {
        Field field = GameManager.class.getDeclaredField("queueIntents");
        field.setAccessible(true);
        return (Map<UUID, GameManager.QueueIntent>) field.get(null);
    }

    private record TestPlayer(UUID id, CookiePlayer cookie, List<String> messages) { }

    private static TestPlayer player() {
        UUID id = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> "PreviewTester";
                    case "isOnline" -> true;
                    case "sendMessage" -> { messages.add(String.valueOf(args[0])); yield null; }
                    default -> defaultValue(method.getReturnType());
                });
        CookiePlayer cookie = new CookiePlayer(player);
        cookie.setState(PlayerState.PERSISTENT_MODE);
        return new TestPlayer(id, cookie, messages);
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
        private final boolean allowed;
        private UUID deniedPlayer;
        TestGame(boolean allowed) { super("PreviewMode"); this.allowed = allowed; }
        @Override public boolean canAdmitPlayer(Player player) {
            return allowed && !player.getUniqueId().equals(deniedPlayer);
        }
        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(CookiePlayer player) { }
        @Override public boolean isGameEnded() { return false; }
    }
}
