package com.cookiebuild.cookiedough.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AdminCommandTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesTheStrictSnakeCaseEnvelope() {
        AdminCommand command = AdminCommand.parse(mapper, """
                {
                  "id":"7de6544f-7dca-431d-ae28-094f744b2f91",
                  "type":"message_player",
                  "target_type":"player",
                  "target_id":"44787eb5-3f21-4565-af67-588c6bf0550f",
                  "payload":{"message":"Hello"},
                  "created_at":"2026-07-15T10:00:00Z",
                  "expires_at":"2026-07-15T10:05:00Z",
                  "idempotency_key":"message:test"
                }
                """);

        assertEquals(AdminCommand.Type.MESSAGE_PLAYER, command.type());
        assertEquals(AdminCommand.TargetType.PLAYER, command.targetType());
        assertEquals("message:test", command.idempotencyKey());
        assertTrue(command.expiredAt(Instant.parse("2026-07-15T10:05:00Z")));
    }

    @Test
    void rejectsWrongTargetKindsAndUnknownCommands() {
        String wrongTarget = validCommand().replace("\"target_type\":\"player\"", "\"target_type\":\"game\"");
        assertThrows(IllegalArgumentException.class, () -> AdminCommand.parse(mapper, wrongTarget));
        assertThrows(IllegalArgumentException.class,
                () -> AdminCommand.parse(mapper, validCommand().replace("message_player", "run_console")));
    }

    @Test
    void rejectsAnExpirationBeforeCreation() {
        String invalid = validCommand().replace("2026-07-15T10:05:00Z", "2026-07-15T09:59:00Z");
        assertThrows(IllegalArgumentException.class, () -> AdminCommand.parse(mapper, invalid));
    }

    @Test
    void acceptsTheTypedUnbanCommandForAPlayer() {
        AdminCommand command = AdminCommand.parse(mapper, validCommand()
                .replace("message_player", "unban")
                .replace("{\"message\":\"Hello\"}", "{\"actor_id\":\"firebase-admin:moderator_123\"}"));
        assertEquals(AdminCommand.Type.UNBAN, command.type());
        assertEquals(AdminCommand.TargetType.PLAYER, command.targetType());
    }

    private static String validCommand() {
        return """
                {"id":"7de6544f-7dca-431d-ae28-094f744b2f91","type":"message_player",
                 "target_type":"player","target_id":"44787eb5-3f21-4565-af67-588c6bf0550f",
                 "payload":{"message":"Hello"},"created_at":"2026-07-15T10:00:00Z",
                 "expires_at":"2026-07-15T10:05:00Z","idempotency_key":"message:test"}
                """;
    }
}
