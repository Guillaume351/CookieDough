package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class RallyGamePreferenceTest {
    private static final UUID VIEWED = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID QUEUED = UUID.fromString("00000000-0000-0000-0000-000000000021");

    @Test
    void externalSpectatorRalliesTheirQueuedArenaInsteadOfTheViewedMatch() {
        assertEquals(QUEUED, RallyManager.preferredRallyGameId(VIEWED, true, QUEUED));
        assertEquals(VIEWED, RallyManager.preferredRallyGameId(VIEWED, false, QUEUED));
        assertEquals(QUEUED, RallyManager.preferredRallyGameId(null, false, QUEUED));
        assertEquals(VIEWED, RallyManager.preferredRallyGameId(VIEWED, true, null));
    }
}
