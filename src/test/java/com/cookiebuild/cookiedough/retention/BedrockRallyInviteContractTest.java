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

        assertTrue(source.contains("BedrockFormSupport.isBedrock(recipient)"));
        assertTrue(source.contains("SimpleForm.builder()"));
        assertTrue(source.contains("\"actions/join\""));
        assertTrue(source.contains("acceptInGameNotice(recipient, notice.id())"));
        assertTrue(source.contains("BedrockFormSupport.send(recipient, builder.build())"));
        // The post-match replay continuation no longer rides on this invite
        // form: "What next?" opens from the lobby-arrival hook instead.
        assertTrue(!source.contains("notifyAvailableAfterMatch"));
    }
}
