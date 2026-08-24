package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AutomaticRallyContractTest {
    @Test
    void automaticAvailabilityOnlyPublishesInGameWhileManualPushesRemainExplicit() throws Exception {
        String rally = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/retention/RallyManager.java"));
        String listener = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/listener/PlayerWrapperListener.java"));

        assertFalse(rally.contains("enqueue(game, RallyRepository.Source.AUTOMATIC"));
        assertFalse(listener.contains("requestSoloLogin"));
        assertTrue(rally.contains("enqueue(game, RallyRepository.Source.PLAYER"));
        assertTrue(rally.contains("enqueue(game, RallyRepository.Source.ADMIN"));
        assertTrue(rally.contains("notices.publish("));
        assertTrue(rally.contains("notices.includeRecipient("));
        assertTrue(rally.contains("GameManager.getAdmittableQueueIntentCount(game)"));
        assertTrue(rally.contains("GameManager.getFirstAdmittableQueueIntentPlayer(game)"));
        assertTrue(rally.contains("partyManager.queueParty(player, game)"));
    }
}
