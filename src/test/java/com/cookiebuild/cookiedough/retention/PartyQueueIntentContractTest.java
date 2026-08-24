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
        String hub = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/PlayerHubMenu.java"));
        String quickPlay = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/commands/QuickPlayCommand.java"));
        String npc = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/GameNPC.java"));
        String wrapper = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/listener/PlayerWrapperListener.java"));

        assertTrue(party.contains("registerPartyQueueIntent(members, game, party.id())"));
        assertFalse(party.contains("added.forEach(addedMember"));
        assertTrue(manager.contains("isCompleteCohort(cohort)"));
        assertTrue(manager.contains("parties.isCurrentQueueCohort"));
        assertTrue(manager.contains("parties.isCurrentSoloQueueCohort"));
        assertTrue(manager.contains("cancelStalePartyCohort(cohort)"));
        assertTrue(manager.contains("lobby.admitQueuedParty(members, game)"));
        assertTrue(lobby.contains("members.stream().anyMatch(member -> !canAdmitQueuedIntent(member, game))"));
        assertTrue(lobby.contains("restorePassiveSource(previous"));
        assertTrue(lobby.contains("requestSelectedActivity(Player player, String activityName)"));
        assertTrue(lobby.contains("presentation != null && presentation.persistent()"));
        assertTrue(lobby.contains("hasOnlinePartyCompanions(player.getUniqueId())"));
        assertTrue(lobby.contains("if (result == null)"));
        assertTrue(hub.contains("requestSelectedActivity(player, gameName)"));
        assertTrue(hub.contains("() -> lobby.requestSelectedActivity(player, gameName)"));
        assertTrue(hub.contains("requestSelectedQuickPlay(player)"));
        assertTrue(hub.contains("if (!LobbyManager.teleportPlayerToLobby(cookiePlayer)) return;"));
        assertTrue(quickPlay.contains("requestSelectedActivity(player, args[0])"));
        assertTrue(quickPlay.contains("requestSelectedQuickPlay(player)"));
        assertTrue(npc.contains("requestSelectedActivity(player, gameName)"));
        assertTrue(wrapper.contains("requestSelectedQuickPlay(player)"));
    }
}
