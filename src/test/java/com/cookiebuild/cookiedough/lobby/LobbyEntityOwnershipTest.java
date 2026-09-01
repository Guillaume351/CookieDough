package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LobbyEntityOwnershipTest {
    @Test
    void removesOnlyCookieBuildOwnedEntitiesDuringStartupReconciliation() {
        assertTrue(LobbyEntityOwnership.cleanupDecision(false, true, false));
        assertTrue(LobbyEntityOwnership.cleanupDecision(false, false, true));
        assertFalse(LobbyEntityOwnership.cleanupDecision(false, false, false));
        assertFalse(LobbyEntityOwnership.cleanupDecision(true, true, true));
    }
}
