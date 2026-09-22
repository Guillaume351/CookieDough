package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NpcReconciliationTest {
    @Test
    void keepsTheCurrentlyTrackedNpcWhenItIsStillPresent() {
        UUID current = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID closerStale = UUID.fromString("00000000-0000-0000-0000-000000000001");

        UUID selected = NpcReconciliation.selectCanonical(List.of(
                new NpcReconciliation.Candidate(closerStale, false, 0.0),
                new NpcReconciliation.Candidate(current, true, 4.0)));

        assertEquals(current, selected);
    }

    @Test
    void adoptsTheClosestPersistedNpcWhenTheTrackedEntityIsGone() {
        UUID closest = UUID.fromString("00000000-0000-0000-0000-000000000003");

        UUID selected = NpcReconciliation.selectCanonical(List.of(
                new NpcReconciliation.Candidate(UUID.randomUUID(), false, 9.0),
                new NpcReconciliation.Candidate(closest, false, 1.0)));

        assertEquals(closest, selected);
    }

    @Test
    void returnsNullWhenNoMarkedNpcExists() {
        assertNull(NpcReconciliation.selectCanonical(List.of()));
    }
}
