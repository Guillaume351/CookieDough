package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PartyQueueCohortSnapshotTest {
    private static final UUID PARTY = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID NEW_MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000013");

    @Test
    void queuedCohortMustExactlyMatchLatestDurablePartyMembership() {
        PartyRepository.Snapshot original = snapshot(List.of(LEADER, MEMBER));
        PartyRepository.Snapshot afterLeave = snapshot(List.of(LEADER));
        PartyRepository.Snapshot afterJoin = snapshot(List.of(LEADER, MEMBER, NEW_MEMBER));

        assertTrue(PartyManager.currentPartyMatches(original, PARTY, List.of(MEMBER, LEADER)));
        assertFalse(PartyManager.currentPartyMatches(afterLeave, PARTY, List.of(LEADER, MEMBER)));
        assertFalse(PartyManager.currentPartyMatches(afterJoin, PARTY, List.of(LEADER, MEMBER)));
        assertFalse(PartyManager.currentPartyMatches(original,
                UUID.fromString("00000000-0000-0000-0000-000000000099"), List.of(LEADER, MEMBER)));
        assertFalse(PartyManager.currentPartyMatches(PartyRepository.Snapshot.empty(), PARTY,
                List.of(LEADER, MEMBER)));
    }

    @Test
    void durablePartyDoesNotBlockSoloQueueUntilACompanionComesOnline() {
        assertFalse(PartyManager.hasOnlineCompanions(MEMBER, List.of(MEMBER)));
        assertTrue(PartyManager.hasOnlineCompanions(MEMBER, List.of(LEADER, MEMBER)));
        assertTrue(PartyManager.currentSoloPlayerHasNoOnlineCompanions(
                PartyRepository.Snapshot.empty(), MEMBER, List.of(MEMBER)));
        assertTrue(PartyManager.currentSoloPlayerHasNoOnlineCompanions(
                snapshot(List.of(LEADER, MEMBER)), MEMBER, List.of(MEMBER)));
        assertFalse(PartyManager.currentSoloPlayerHasNoOnlineCompanions(
                snapshot(List.of(LEADER, MEMBER)), MEMBER, List.of(LEADER, MEMBER)));
    }

    private static PartyRepository.Snapshot snapshot(List<UUID> members) {
        return PartyRepository.Snapshot.of(List.of(new PartyRepository.Party(PARTY, LEADER, members)));
    }
}
