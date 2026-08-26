package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class BaseEventBlockerPolicyTest {
    @Test
    void onlyCreativeOperatorsMayEditTheLobby() {
        assertTrue(BaseEventBlocker.canEditLobby("lobby", true, GameMode.CREATIVE));
        assertTrue(BaseEventBlocker.canEditLobby("LOBBY", true, GameMode.CREATIVE));

        assertFalse(BaseEventBlocker.canEditLobby("lobby", false, GameMode.CREATIVE));
        assertFalse(BaseEventBlocker.canEditLobby("lobby", true, GameMode.SURVIVAL));
        assertFalse(BaseEventBlocker.canEditLobby("lobby", true, GameMode.ADVENTURE));
        assertFalse(BaseEventBlocker.canEditLobby("pitchout_match_123", true, GameMode.CREATIVE));
        assertFalse(BaseEventBlocker.canEditLobby(null, true, GameMode.CREATIVE));
    }
}
