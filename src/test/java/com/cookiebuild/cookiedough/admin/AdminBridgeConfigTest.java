package com.cookiebuild.cookiedough.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AdminBridgeConfigTest {
    @Test
    void isDisabledByDefault() {
        AdminBridgeConfig config = AdminBridgeConfig.from(Map.of());
        assertFalse(config.enabled());
        assertEquals("commands.minecraft-1", config.serverCommandRoutingKey());
    }

    @Test
    void parsesBoundedOperationalSettings() {
        AdminBridgeConfig config = AdminBridgeConfig.from(Map.of(
                "ADMIN_BRIDGE_ENABLED", "true",
                "ADMIN_BRIDGE_SERVER_ID", "paper-eu-1",
                "ADMIN_BRIDGE_SNAPSHOT_SECONDS", "10",
                "ADMIN_BRIDGE_COMMAND_TTL_MS", "120000"));
        assertTrue(config.enabled());
        assertEquals(200L, config.snapshotIntervalTicks());
        assertEquals(120_000L, config.commandTtlMillis());
        assertEquals("events.paper-eu-1.snapshot", config.eventRoutingKey("snapshot"));
        assertEquals("cookiebuild.admin.paper-eu-1.commands.dead", config.deadLetterQueue());
        assertEquals("dead.commands.paper-eu-1", config.deadLetterRoutingKey());
    }

    @Test
    void rejectsUnsafeServerIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> AdminBridgeConfig.from(Map.of(
                "ADMIN_BRIDGE_ENABLED", "true", "ADMIN_BRIDGE_SERVER_ID", "../../bad")));
    }
}
