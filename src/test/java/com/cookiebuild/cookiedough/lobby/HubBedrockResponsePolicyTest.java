package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HubBedrockResponsePolicyTest {
    @Test
    void queueFormRequiresSameQueuedAndCurrentGame() {
        assertTrue(HubBedrockResponsePolicy.queueValid("SkyWars", "skywars", true, "SKYWARS"));
        assertFalse(HubBedrockResponsePolicy.queueValid("SkyWars", "BedWars", true, "SkyWars"));
        assertFalse(HubBedrockResponsePolicy.queueValid("SkyWars", "SkyWars", false, "SkyWars"));
        assertFalse(HubBedrockResponsePolicy.queueValid("SkyWars", "SkyWars", true, null));
    }

    @Test
    void replayFormCannotInterruptANewActivity() {
        assertTrue(HubBedrockResponsePolicy.replayValid(true, false));
        assertFalse(HubBedrockResponsePolicy.replayValid(false, false));
        assertFalse(HubBedrockResponsePolicy.replayValid(true, true));
    }
}
