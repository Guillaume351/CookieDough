package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.player.PlayerState;

class LobbyFlightPolicyTest {
    @Test
    void selectedFlightWorksInLobbyAndWhileQueuedButNeverInAMatchOrAnotherWorld() {
        assertTrue(LobbyFlightPolicy.shouldEnable(true, true, PlayerState.LOBBY));
        assertTrue(LobbyFlightPolicy.shouldEnable(true, true, PlayerState.QUEUED));
        assertFalse(LobbyFlightPolicy.shouldEnable(true, false, PlayerState.LOBBY));
        assertFalse(LobbyFlightPolicy.shouldEnable(true, true, PlayerState.IN_GAME));
        assertFalse(LobbyFlightPolicy.shouldEnable(false, true, PlayerState.LOBBY));
    }

    @Test
    void creativeSpectatorAndStaffFlightAreProtectedFromCosmeticCleanup() {
        assertTrue(LobbyFlightPolicy.preservesIndependentFlight(GameMode.CREATIVE, false));
        assertTrue(LobbyFlightPolicy.preservesIndependentFlight(GameMode.SPECTATOR, false));
        assertTrue(LobbyFlightPolicy.preservesIndependentFlight(GameMode.SURVIVAL, true));
        assertFalse(LobbyFlightPolicy.preservesIndependentFlight(GameMode.SURVIVAL, false));
    }

    @Test
    void transitionFallSafetyNeverCancelsArenaDamage() {
        assertTrue(LobbyFlightPolicy.cancelsTransitionFallDamage(true, true));
        assertFalse(LobbyFlightPolicy.cancelsTransitionFallDamage(true, false));
        assertFalse(LobbyFlightPolicy.cancelsTransitionFallDamage(false, true));
    }
}
