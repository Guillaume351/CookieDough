package com.cookiebuild.cookiedough.admin;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Strict wire contract for commands accepted from the admin control plane. */
public record AdminCommand(
        UUID id,
        Type type,
        TargetType targetType,
        String targetId,
        ObjectNode payload,
        Instant createdAt,
        Instant expiresAt,
        String idempotencyKey) {

    public enum Type {
        MESSAGE_ALL,
        MESSAGE_LOBBY,
        MESSAGE_GAME,
        MESSAGE_PLAYER,
        KICK,
        RETURN_LOBBY,
        BAN_TEMP,
        BAN_PERMANENT,
        MUTE,
        UNMUTE,
        UNBAN,
        RALLY,
        CLOSE_ADMISSIONS,
        REOPEN_ADMISSIONS,
        SAFE_CANCEL,
        MAINTENANCE,
        DRAIN,
        RESTART_READY;

        static Type fromWire(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Unsupported admin command type: " + value, error);
            }
        }
    }

    public enum TargetType {
        NONE,
        SERVER,
        GAME,
        PLAYER;

        static TargetType fromWire(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Unsupported admin target type: " + value, error);
            }
        }
    }

    private static final Set<Type> PLAYER_COMMANDS = Set.of(
            Type.MESSAGE_PLAYER, Type.KICK, Type.RETURN_LOBBY, Type.BAN_TEMP,
            Type.BAN_PERMANENT, Type.MUTE, Type.UNMUTE, Type.UNBAN);
    private static final Set<Type> GAME_COMMANDS = Set.of(
            Type.MESSAGE_GAME, Type.CLOSE_ADMISSIONS, Type.REOPEN_ADMISSIONS, Type.SAFE_CANCEL);
    private static final Set<Type> SERVER_COMMANDS = Set.of(
            Type.MESSAGE_ALL, Type.MESSAGE_LOBBY, Type.MAINTENANCE, Type.DRAIN, Type.RESTART_READY);

    public AdminCommand {
        if (id == null || type == null || targetType == null || payload == null
                || createdAt == null || expiresAt == null) {
            throw new IllegalArgumentException("Admin command id, type, target, payload, and timestamps are required");
        }
        if (expiresAt.isBefore(createdAt) || expiresAt.equals(createdAt)) {
            throw new IllegalArgumentException("Admin command expiration must be after creation");
        }
        targetId = targetId == null || targetId.isBlank() ? null : targetId.trim();
        idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? id.toString() : idempotencyKey.trim();
        if (idempotencyKey.length() > 160) {
            throw new IllegalArgumentException("Admin command idempotency key is too long");
        }
        if (PLAYER_COMMANDS.contains(type) && (targetType != TargetType.PLAYER || targetId == null)) {
            throw new IllegalArgumentException(type + " requires a player target");
        }
        if (GAME_COMMANDS.contains(type) && (targetType != TargetType.GAME || targetId == null)) {
            throw new IllegalArgumentException(type + " requires a game target");
        }
        if (SERVER_COMMANDS.contains(type) && targetType != TargetType.SERVER) {
            throw new IllegalArgumentException(type + " requires a server target");
        }
        if (type == Type.RALLY && targetType != TargetType.SERVER && targetType != TargetType.GAME) {
            throw new IllegalArgumentException("RALLY requires a server or game target");
        }
    }

    public static AdminCommand parse(ObjectMapper mapper, String json) {
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isObject()) {
                throw new IllegalArgumentException("Admin command must be a JSON object");
            }
            JsonNode payload = root.path("payload");
            if (!payload.isObject()) {
                throw new IllegalArgumentException("Admin command payload must be a JSON object");
            }
            return new AdminCommand(
                    UUID.fromString(requiredText(root, "id")),
                    Type.fromWire(requiredText(root, "type")),
                    TargetType.fromWire(requiredText(root, "target_type")),
                    optionalText(root, "target_id"),
                    ((ObjectNode) payload).deepCopy(),
                    parseInstant(root, "created_at"),
                    parseInstant(root, "expires_at"),
                    optionalText(root, "idempotency_key"));
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid admin command JSON", error);
        }
    }

    public boolean expiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }

    private static String requiredText(JsonNode root, String field) {
        String value = optionalText(root, field);
        if (value == null) {
            throw new IllegalArgumentException("Missing admin command field: " + field);
        }
        return value;
    }

    private static String optionalText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()
                ? null : value.asText().trim();
    }

    private static Instant parseInstant(JsonNode root, String field) {
        try {
            return Instant.parse(requiredText(root, field));
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Invalid ISO-8601 timestamp in " + field, error);
        }
    }
}
