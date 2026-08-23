package com.cookiebuild.cookiedough.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BedrockFormImagesTest {
    @Test
    void customPackPathsAreCentralizedAndVersionFlagIsFailClosed() {
        assertEquals("textures/ui/cookiebuild/modes/skyblock.png",
                BedrockFormImages.path("modes/skyblock", true).orElseThrow());
        assertEquals("textures/ui/cookiebuild/actions/games.png",
                BedrockFormImages.path("actions/games", true).orElseThrow());
        assertEquals("textures/ui/cookiebuild/kits/skywars/scout.png",
                BedrockFormImages.path("kits/skywars/scout", true).orElseThrow());
        assertEquals("textures/ui/cookiebuild/bedwars/offers/wool.png",
                BedrockFormImages.path("bedwars/offers/wool", true).orElseThrow());
        assertTrue(BedrockFormImages.path("modes/skyblock", false).isEmpty());
        assertTrue(BedrockFormImages.path("../escape", true).isEmpty());
        assertTrue(BedrockFormImages.path("kits/skywars/unknown", true).isEmpty());
        assertTrue(BedrockFormImages.path("bedwars/offers/unknown", true).isEmpty());
        assertEquals(89, BedrockFormImages.knownImageIds().size());
        assertTrue(BedrockFormImages.isKnown("actions/palette"));
        assertFalse(BedrockFormImages.isKnown("actions/select"));
        assertTrue(BedrockFormImages.enabled("true"));
        assertTrue(!BedrockFormImages.enabled(null));
    }
}
