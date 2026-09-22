package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class StandbyGamePoolTest {
    @Test
    void promotesPreparedGamesInOrderWithoutConstructingFallbacks() {
        StandbyGamePool<String> pool = new StandbyGamePool<>(2);

        assertTrue(pool.offer("first"));
        assertTrue(pool.offer("second"));
        assertFalse(pool.offer("unbounded"));

        assertEquals("first", pool.poll());
        assertEquals("second", pool.poll());
        assertNull(pool.poll());
        assertTrue(pool.needsRefill());
    }

    @Test
    void drainsPreparedGamesForPluginShutdown() {
        StandbyGamePool<String> pool = new StandbyGamePool<>(2);
        pool.offer("first");
        pool.offer("second");

        assertEquals(List.of("first", "second"), pool.drain());
        assertEquals(0, pool.size());
    }

    @Test
    void rejectsInvalidTargetsAndNullGames() {
        assertThrows(IllegalArgumentException.class, () -> new StandbyGamePool<>(0));
        StandbyGamePool<String> pool = new StandbyGamePool<>(1);
        assertThrows(NullPointerException.class, () -> pool.offer(null));
    }

    @Test
    void oneStandbySupportsRepeatedRematchesWhileLobbyPlayersStayOnline() {
        StandbyGamePool<String> pool = new StandbyGamePool<>(StandbyRefillPolicy.TARGET_SIZE);
        assertTrue(pool.offer("startup-standby"));

        // Online lobby players are intentionally absent from the policy: each
        // promotion is followed by one bounded refill, so the pool remains
        // available across a long replay chain without an empty-server window.
        for (int match = 1; match <= 12; match++) {
            assertTrue(pool.poll() != null, "rematch " + match + " needs a prepared arena");
            assertEquals(1, StandbyRefillPolicy.runtimeBatchSize(pool.size(), pool.targetSize()));
            assertTrue(pool.offer("runtime-standby-" + match));
        }
    }
}
