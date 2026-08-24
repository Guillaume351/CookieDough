package com.cookiebuild.cookiedough.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QueueIntentReadinessPolicyTest {
    @Test
    void waitsInPassiveActivityUntilIntentsCanReachMinimum() {
        assertFalse(QueueIntentReadinessPolicy.shouldActivate(0, 1, 2, 8));
        assertTrue(QueueIntentReadinessPolicy.shouldActivate(1, 1, 2, 8));
        assertTrue(QueueIntentReadinessPolicy.shouldActivate(0, 2, 2, 8));
        assertFalse(QueueIntentReadinessPolicy.shouldActivate(8, 1, 2, 8));
    }
}
