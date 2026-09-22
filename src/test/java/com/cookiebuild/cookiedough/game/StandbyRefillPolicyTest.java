package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StandbyRefillPolicyTest {
    @Test
    void runtimeRefillIsAlwaysOneArenaAndNeverNeedsAnEmptyServerSignal() {
        assertEquals(1, StandbyRefillPolicy.runtimeBatchSize(0, 1));
        assertEquals(1, StandbyRefillPolicy.runtimeBatchSize(1, 2));
        assertEquals(0, StandbyRefillPolicy.runtimeBatchSize(1, 1));
    }

    @Test
    void failedLoadsBackOffWithoutChangingTheUnitBatch() {
        assertEquals(1L, StandbyRefillPolicy.nextDelayTicks(true));
        assertEquals(100L, StandbyRefillPolicy.nextDelayTicks(false));
    }

    @Test
    void reportsArenaLoadsThatExceedTheMainThreadBudget() {
        assertFalse(StandbyRefillPolicy.exceedsLoadBudget(250L));
        assertTrue(StandbyRefillPolicy.exceedsLoadBudget(251L));
        assertThrows(IllegalArgumentException.class, () -> StandbyRefillPolicy.exceedsLoadBudget(-1L));
    }

    @Test
    void rejectsImpossiblePoolSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> StandbyRefillPolicy.runtimeBatchSize(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> StandbyRefillPolicy.runtimeBatchSize(2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> StandbyRefillPolicy.runtimeBatchSize(0, 0));
    }
}
