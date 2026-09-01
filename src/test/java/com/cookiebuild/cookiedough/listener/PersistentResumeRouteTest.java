package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PersistentResumeRouteTest {
    @Test
    void unknownDurableMarkerIsClearedInsteadOfCreatingAnInfiniteHold() {
        assertEquals(new PersistentResumeRoute(false, true),
                PersistentResumeRoute.resolve("removed-mode", false, false));
    }

    @Test
    void knownTemporarilyUnavailableProviderPreservesMarkerAndInventoryForRetry() {
        assertEquals(new PersistentResumeRoute(true, false),
                PersistentResumeRoute.resolve("Skyblock", false, false));
    }

    @Test
    void registeredActivityAndOwnedWorldPreservePlayerState() {
        assertEquals(new PersistentResumeRoute(true, false),
                PersistentResumeRoute.resolve("Skyblock", true, false));
        assertEquals(new PersistentResumeRoute(true, false),
                PersistentResumeRoute.resolve(null, true, true));
    }

    @Test
    void ordinaryLobbyJoinDoesNotEnterPersistentRecovery() {
        assertEquals(new PersistentResumeRoute(false, false),
                PersistentResumeRoute.resolve(null, false, false));
    }
}
