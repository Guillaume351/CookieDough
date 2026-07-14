package com.cookiebuild.cookiedough.retention;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Durable producer contract for structured player-call notifications. */
public interface RallyRepository {
    Pattern ACTOR_DISPLAY_NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_. -]{1,32}$");

    Set<String> SUPPORTED_GAMEMODES = Set.of(
            "microbattles", "pitchout", "skywars", "buildbattles");

    enum Source {
        PLAYER,
        AUTOMATIC;

        public String wireValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    enum EnqueueStatus {
        ENQUEUED,
        GLOBAL_COOLDOWN,
        GAMEMODE_COOLDOWN,
        PLAYER_COOLDOWN,
        AUTOMATIC_COOLDOWN,
        DUPLICATE
    }

    record Request(
            UUID rallyId,
            Source source,
            String gamemode,
            int queuedCount,
            int neededCount,
            String actorDisplayName) {
        public Request {
            if (rallyId == null || source == null || gamemode == null) {
                throw new IllegalArgumentException("Rally id, source, and gamemode are required");
            }
            gamemode = gamemode.toLowerCase(Locale.ROOT);
            if (!SUPPORTED_GAMEMODES.contains(gamemode)) {
                throw new IllegalArgumentException("Unsupported rally gamemode: " + gamemode);
            }
            if (queuedCount < 0 || neededCount < 1) {
                throw new IllegalArgumentException("Invalid rally queue counts");
            }
            if (source == Source.PLAYER) {
                actorDisplayName = actorDisplayName == null ? null : actorDisplayName.trim();
                if (actorDisplayName == null
                        || actorDisplayName.length() > 32
                        || !ACTOR_DISPLAY_NAME_PATTERN.matcher(actorDisplayName).matches()) {
                    throw new IllegalArgumentException("Player rallies require a public Minecraft name");
                }
            } else if (actorDisplayName != null) {
                throw new IllegalArgumentException("Automatic rallies cannot name an actor");
            }
        }
    }

    record EnqueueResult(EnqueueStatus status, UUID outboxId, Instant availableAt) {
        public static EnqueueResult rejected(EnqueueStatus status) {
            return new EnqueueResult(status, null, null);
        }
    }

    EnqueueResult enqueue(Request request);

    boolean cancel(UUID outboxId);
}
