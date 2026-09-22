package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatueManagerTest {
    @Test
    void championLabelsOnlyAppearNearTheirNpc() {
        assertTrue(StatueManager.isLabelVisible(0));
        assertTrue(StatueManager.isLabelVisible(100));
        assertFalse(StatueManager.isLabelVisible(100.01));
        assertFalse(StatueManager.isLabelVisible(-1));
    }
}
