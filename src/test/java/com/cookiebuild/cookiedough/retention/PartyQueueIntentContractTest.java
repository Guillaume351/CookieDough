package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PartyQueueIntentContractTest {
    @Test
    void passivePartyUsesOneCohortAndNeverSequentialDirectAdmission() throws Exception {
        String party = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/retention/PartyManager.java"));
        String manager = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/game/GameManager.java"));
        String lobby = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/LobbyManager.java"));

        assertTrue(party.contains("registerPartyQueueIntent(members, game, party.id())"));
        assertFalse(party.contains("added.forEach(addedMember"));
        assertTrue(manager.contains("isCompleteCohort(cohort)"));
        assertTrue(manager.contains("parties.isCurrentQueueCohort"));
        assertTrue(manager.contains("parties.isCurrentSoloQueueCohort"));
        assertTrue(manager.contains("cancelStalePartyCohort(cohort)"));
        assertTrue(manager.contains("lobby.admitQueuedParty(members, game)"));
        assertTrue(lobby.contains("members.stream().anyMatch(member -> !canAdmitQueuedIntent(member, game))"));
        assertTrue(lobby.contains("restorePassiveSource(previous"));
    }
}
