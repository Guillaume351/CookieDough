package com.cookiebuild.cookiedough.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FeedbackCommandTest {
    @Test
    void prefersDedicatedFeedbackWebhookAndFallsBackToExistingStatusChannel() {
        assertEquals("dedicated", FeedbackCommand.selectWebhook("dedicated", "status"));
        assertEquals("status", FeedbackCommand.selectWebhook(" ", "status"));
        assertNull(FeedbackCommand.selectWebhook(null, " "));
    }

    @Test
    void normalizesAndBoundsPlayerControlledFeedback() {
        assertEquals("hello world", FeedbackCommand.normalizeMessage(new String[] {"hello\nworld"}));
        assertEquals(300, FeedbackCommand.normalizeMessage(new String[] {"x".repeat(350)}).length());
    }
}
