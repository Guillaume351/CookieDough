package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class QueueIntentReadinessPolicyTest {
    @Test
    void waitsInPassiveActivityUntilIntentsCanReachMinimum() {
        assertFalse(QueueIntentReadinessPolicy.shouldActivate(0, 1, 2, 8));
        assertTrue(QueueIntentReadinessPolicy.shouldActivate(1, 1, 2, 8));
        assertTrue(QueueIntentReadinessPolicy.shouldActivate(0, 2, 2, 8));
        assertFalse(QueueIntentReadinessPolicy.shouldActivate(8, 1, 2, 8));
    }

    @Test
    void queueIntentEligibilityRequiresTheAuthoritativeLoadedProfile() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/cookiedough/game/GameManager.java"));
        String predicate = source.substring(source.indexOf(
                "private static boolean isQueueIntentEligible(CookiePlayer current)"));

        assertTrue(predicate.contains("PlayerWrapperListener.isPlayerDataReady("));
    }
}
