package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DirectAdmissionQueueIntentContractTest {
    @Test
    void successfulNpcAndSignAdmissionsClearAStaleQueueIntentAfterAdmission() throws Exception {
        String npc = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/GameNPC.java"));
        String lobby = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/lobby/LobbyManager.java"));

        assertCancelFollowsSuccessfulAdmission(npc);
        String signAdmission = lobby.substring(lobby.indexOf("if (cookiePlayer.getState() == PlayerState.LOBBY)"),
                lobby.indexOf("private Game findGameForSign"));
        assertCancelFollowsSuccessfulAdmission(signAdmission);
    }

    private static void assertCancelFollowsSuccessfulAdmission(String source) {
        int admission = source.indexOf("game.addPlayerToAvailableTeam(cookiePlayer)");
        int cancellation = source.indexOf("GameManager.cancelQueueIntent(player.getUniqueId())", admission);
        assertTrue(admission >= 0);
        assertTrue(cancellation > admission);
    }
}
