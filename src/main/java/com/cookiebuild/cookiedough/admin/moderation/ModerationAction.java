package com.cookiebuild.cookiedough.admin.moderation;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Durable moderation action mirrored by the website moderation_actions table. */
public record ModerationAction(
        UUID id,
        UUID playerId,
        String playerName,
        Type type,
        String reason,
        String actorId,
        String actorDisplayName,
        Instant startsAt,
        Instant expiresAt,
        UUID sourceCommandId,
        String metadataJson,
        Instant createdAt) {

    public enum Type {
        BAN,
        MUTE;

        public String wireValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Type fromWire(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    public ModerationAction {
        if (id == null || playerId == null || type == null || startsAt == null || createdAt == null) {
            throw new IllegalArgumentException("Moderation action id, player, type, and timestamps are required");
        }
        actorId = validateActorId(actorId);
        playerName = sanitize(playerName, 64, "Unknown player");
        reason = sanitize(reason, 500, "No reason supplied");
        actorDisplayName = sanitize(actorDisplayName, 120, "Admin");
        metadataJson = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
        if (expiresAt != null && !expiresAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Moderation action expiration must be after its start");
        }
    }

    public boolean activeAt(Instant instant) {
        return !startsAt.isAfter(instant) && (expiresAt == null || expiresAt.isAfter(instant));
    }

    private static String sanitize(String value, int maximumLength, String fallback) {
        String sanitized = value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ").trim();
        if (sanitized.isEmpty()) sanitized = fallback;
        return sanitized.length() <= maximumLength ? sanitized : sanitized.substring(0, maximumLength);
    }

    private static String validateActorId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Firebase admin actor id is required");
        }
        String actorId = value.trim();
        if (actorId.length() > 128 || actorId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Firebase admin actor id must contain 1 to 128 non-control characters");
        }
        return actorId;
    }
}
