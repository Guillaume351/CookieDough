package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BedrockRallyInviteContractTest {
    @Test
    void nativeFormUsesSameOneTimeNoticeAcceptanceAndImageBackedButtons() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/retention/RallyManager.java"));
        String game = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/game/Game.java"));

        assertTrue(source.contains("BedrockFormSupport.isBedrock(recipient)"));
        assertTrue(source.contains("SimpleForm.builder()"));
        assertTrue(source.contains("\"actions/join\""));
        assertTrue(source.contains("acceptInGameNotice(recipient, notice.id())"));
        assertTrue(source.contains("BedrockFormSupport.send(recipient, builder.build())"));
        assertTrue(source.contains("builder.closedOrInvalidResultHandler"));
        assertTrue(source.contains("plugin.getPlayerHubMenu().openReplay(player, completedGameName)"));
        assertTrue(game.contains("if (!bedrockQueueOffer"));
    }
}
