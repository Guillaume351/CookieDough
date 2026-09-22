package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.activity.ActivityAdmissionResult;
import com.cookiebuild.cookiedough.activity.ActivityRegistry;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PersistentRecoveryOwnershipTest {
    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final CookiePlayer cookie = new CookiePlayer(player);
    private final PersistentActivityRecovery recovery = new PersistentActivityRecovery();
    private PlayerWrapperListener listener;
    private Object previousInstance;

    @BeforeEach
    void pendingSavedInventory() throws Exception {
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.locale()).thenReturn(Locale.ENGLISH);
        cookie.setState(PlayerState.PERSISTENT_MODE);
        listener = mock(PlayerWrapperListener.class, CALLS_REAL_METHODS);
        Field tracker = PlayerWrapperListener.class.getDeclaredField("persistentRecovery");
        tracker.setAccessible(true);
        tracker.set(listener, recovery);
        Field instance = PlayerWrapperListener.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previousInstance = instance.get(null);
        instance.set(null, listener);
        recovery.hold(id, "Skyblock", 0);
    }

    @AfterEach
    void restoreStaticListener() throws Exception {
        Field instance = PlayerWrapperListener.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previousInstance);
        GameManager.cancelQueueIntent(id);
    }

    @Test
    void pendingRecoveryRefusesHubLeaveAndKeepsDurableInventoryUntouched() {
        assertFalse(ActivityRegistry.canLeave(cookie, "returned_lobby"));
        assertFalse(ActivityRegistry.leave(cookie, "returned_lobby"));
        assertTrue(recovery.isHolding(id));
        verify(player, never()).getInventory();
        verify(player, never()).getPersistentDataContainer();
        assertEquals(PlayerState.PERSISTENT_MODE, cookie.getState());
    }

    @Test
    void pendingRecoveryRefusesSoloPartyAndDirectGameAdmissions() {
        TestGame game = new TestGame();
        assertFalse(GameManager.registerQueueIntent(cookie, game));
        assertFalse(GameManager.registerPartyQueueIntent(List.of(cookie), game, UUID.randomUUID()));
        assertNull(GameManager.getQueueIntent(id));
        // Defend the final boundary even if an older caller already changed player state.
        cookie.setState(PlayerState.LOBBY);
        assertFalse(game.addPlayer(cookie));
        assertFalse(game.addSpectator(cookie));
        assertEquals(0, game.getPlayerCount());
        assertTrue(recovery.isHolding(id));
        verify(player, never()).getInventory();
    }

    @Test
    void pendingRecoveryCannotEnterAnotherPersistentActivity() {
        assertFalse(ActivityRegistry.enter("AnotherActivity", cookie).admitted());
        assertTrue(PlayerWrapperListener.isRecoveringActivity(id, "skyblock"));
        assertTrue(recovery.isHolding(id));
        verify(player, never()).getInventory();
    }

    @Test
    void staleRetryCannotSeizeNewerGameOrRemoveItsRecoveryTicket() throws Exception {
        cookie.setState(PlayerState.IN_GAME);
        TestGame owned = new TestGame();
        try (var games = mockStatic(GameManager.class)) {
            games.when(() -> GameManager.getGameOfPlayer(cookie)).thenReturn(owned);
            assertFalse(resume());
            Method hold = PlayerWrapperListener.class.getDeclaredMethod("holdPersistentActivity",
                    Player.class, CookiePlayer.class, String.class, boolean.class);
            hold.setAccessible(true);
            hold.invoke(listener, player, cookie, "Skyblock", false);
        }
        assertEquals(PlayerState.IN_GAME, cookie.getState());
        assertTrue(recovery.isHolding(id));
        verify(player, never()).closeInventory();
        verify(player, never()).setInvulnerable(true);
        verify(player, never()).getWorld();
    }

    @Test
    void successfulResumeReleasesAdmissionBlockWithoutClearingSavedMarker() throws Exception {
        CookieDough plugin = mock(CookieDough.class);
        PlayerTransitionFlightGuard guard = mock(PlayerTransitionFlightGuard.class);
        when(plugin.getPlayerTransitionFlightGuard()).thenReturn(guard);
        try (var plugins = mockStatic(CookieDough.class);
                var activities = mockStatic(ActivityRegistry.class);
                var games = mockStatic(GameManager.class)) {
            plugins.when(CookieDough::getInstance).thenReturn(plugin);
            activities.when(() -> ActivityRegistry.enter("Skyblock", cookie))
                    .thenReturn(ActivityAdmissionResult.admitted(""));
            assertTrue(resume());
            assertFalse(PlayerWrapperListener.isPersistentRecoveryPending(id));
            activities.verify(() -> ActivityRegistry.clearResume(player), never());
        }
        assertTrue(ActivityRegistry.canLeave(cookie, "returned_lobby"));
        verify(player).setInvulnerable(false);
        verify(player, never()).getInventory();
        verify(player, never()).getPersistentDataContainer();
    }

    @Test
    void legacyOverlappingGameCanEjectWithoutDiscardingRecoveryMarker() {
        cookie.setState(PlayerState.IN_GAME);
        TestGame owned = new TestGame();
        try (var games = mockStatic(GameManager.class)) {
            games.when(() -> GameManager.getGameOfPlayer(cookie)).thenReturn(owned);
            assertTrue(ActivityRegistry.canLeave(cookie, "returned_lobby"));
            assertTrue(ActivityRegistry.leave(cookie, "returned_lobby"));
        }
        assertTrue(recovery.isHolding(id));
        verify(player, never()).getPersistentDataContainer();
        verify(player, never()).getInventory();
        // Once the game owner is gone, its pending saved inventory is protected again.
        cookie.setState(PlayerState.PERSISTENT_MODE);
        assertFalse(ActivityRegistry.canLeave(cookie, "returned_lobby"));
    }

    @Test
    void directSuccessfulReturnToSameActivityReleasesQuarantineImmediately() {
        var activity = mock(com.cookiebuild.cookiedough.activity.PersistentActivity.class);
        when(activity.name()).thenReturn("Skyblock");
        when(activity.isAvailable()).thenReturn(true);
        when(activity.enter(cookie)).thenReturn(ActivityAdmissionResult.admitted("Restored"));
        ActivityRegistry.register(activity);
        try {
            assertTrue(ActivityRegistry.enter("Skyblock", cookie).admitted());
            assertFalse(recovery.isHolding(id));
            assertTrue(ActivityRegistry.canLeave(cookie, "returned_lobby"));
            verify(player).setInvulnerable(false);
            verify(player, never()).getPersistentDataContainer();
            verify(player, never()).getInventory();
        } finally {
            ActivityRegistry.unregister(activity);
        }
    }

    private boolean resume() throws Exception {
        Method method = PlayerWrapperListener.class.getDeclaredMethod("attemptPersistentResume",
                Player.class, CookiePlayer.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(listener, player, cookie, "Skyblock");
    }

    private static final class TestGame extends Game {
        TestGame() { super("RecoveryTest"); }
        @Override public void registerANewGame() { }
        @Override protected void teleportToGame(CookiePlayer player) { }
        @Override public boolean isGameEnded() { return false; }
    }
}
