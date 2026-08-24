package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PersistentResumeRouteTest {
    @Test
    void unknownDurableMarkerIsClearedInsteadOfCreatingAnInfiniteHold() {
        assertEquals(new PersistentResumeRoute(false, true),
                PersistentResumeRoute.resolve(true, false, false));
    }

    @Test
    void registeredActivityAndOwnedWorldPreservePlayerState() {
        assertEquals(new PersistentResumeRoute(true, false),
                PersistentResumeRoute.resolve(true, true, false));
        assertEquals(new PersistentResumeRoute(true, false),
                PersistentResumeRoute.resolve(false, true, true));
    }

    @Test
    void ordinaryLobbyJoinDoesNotEnterPersistentRecovery() {
        assertEquals(new PersistentResumeRoute(false, false),
                PersistentResumeRoute.resolve(false, false, false));
    }
}
