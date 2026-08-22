package com.cookiebuild.cookiedough.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BedrockFormImagesTest {
    @Test
    void customPackPathsAreCentralizedAndVersionFlagIsFailClosed() {
        assertEquals("textures/ui/cookiebuild/modes/skyblock.png",
                BedrockFormImages.path("modes/skyblock", true).orElseThrow());
        assertTrue(BedrockFormImages.path("modes/skyblock", false).isEmpty());
        assertTrue(BedrockFormImages.path("../escape", true).isEmpty());
        assertTrue(BedrockFormImages.path("kits/skywars/scout", true).isEmpty());
        assertTrue(BedrockFormImages.path("bedwars/offers/wool", true).isEmpty());
        assertTrue(BedrockFormImages.enabled("true"));
        assertTrue(!BedrockFormImages.enabled(null));
    }
}
