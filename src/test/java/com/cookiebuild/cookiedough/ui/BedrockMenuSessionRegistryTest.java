package com.cookiebuild.cookiedough.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BedrockMenuSessionRegistryTest {
    @Test
    void responseIsSingleUseAndBoundToItsScope() {
        BedrockMenuSessionRegistry registry = new BedrockMenuSessionRegistry();
        UUID player = UUID.randomUUID();
        UUID nonce = registry.issue(player, "kits:index");

        assertFalse(registry.consume(player, nonce, "kits:detail"));
        assertTrue(registry.consume(player, nonce, "kits:index"));
        assertFalse(registry.consume(player, nonce, "kits:index"));
    }

    @Test
    void staleCloseCannotInvalidateANewerPage() {
        BedrockMenuSessionRegistry registry = new BedrockMenuSessionRegistry();
        UUID player = UUID.randomUUID();
        UUID stale = registry.issue(player, "vote:first");
        UUID current = registry.issue(player, "vote:second");

        registry.invalidate(player, stale, "vote:first");

        assertTrue(registry.consume(player, current, "vote:second"));
        assertEquals(0, registry.size());
    }
}
