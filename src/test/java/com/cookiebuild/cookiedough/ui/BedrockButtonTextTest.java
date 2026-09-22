package com.cookiebuild.cookiedough.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BedrockButtonTextTest {
    @Test
    void hierarchyRemainsReadableWithoutColourSupport() {
        String text = BedrockButtonText.format("§a§lPlay", "§7Join the queue");

        assertEquals("▶ Play\n↳ Join the queue", text);
        assertFalse(text.contains("§"));
    }

    @Test
    void labelOnlyAndBlankLabelAreExplicit() {
        assertEquals("▶ Back", BedrockButtonText.format("Back"));
        assertThrows(IllegalArgumentException.class, () -> BedrockButtonText.format("  "));
    }
}
