package com.cookiebuild.cookiedough.retention;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Durable producer contract for structured player-call notifications. */
public interface RallyRepository {
    Pattern ACTOR_DISPLAY_NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_. -]{1,32}$");

    Set<String> SUPPORTED_GAMEMODES = Set.of(
            "microbattles", "pitchout", "skywars", "buildbattles", "turfwars");
    String NETWORK_GAMEMODE = "network";

    enum Source {
        LOGIN,
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
            String actorDisplayName,
            UUID targetPlayerId,
            UUID gameId) {
        public Request {
            if (rallyId == null || source == null || gamemode == null || targetPlayerId == null) {
                throw new IllegalArgumentException("Rally id, source, gamemode, and target player are required");
            }
            gamemode = gamemode.toLowerCase(Locale.ROOT);
            boolean login = source == Source.LOGIN;
            if (login ? !NETWORK_GAMEMODE.equals(gamemode) : !SUPPORTED_GAMEMODES.contains(gamemode)) {
                throw new IllegalArgumentException("Unsupported rally gamemode: " + gamemode);
            }
            if (queuedCount < 0 || neededCount < 0
                    || (login && (queuedCount != 0 || neededCount != 0))
                    || (source == Source.PLAYER && neededCount < 1)) {
                throw new IllegalArgumentException("Invalid rally queue counts");
            }
            if (login ? gameId != null : gameId == null) {
                throw new IllegalArgumentException("Login rallies cannot target a game; game rallies must target one");
            }
            if (source == Source.PLAYER || login) {
                actorDisplayName = actorDisplayName == null ? null : actorDisplayName.trim();
                if (actorDisplayName == null
                        || actorDisplayName.length() > 32
                        || !ACTOR_DISPLAY_NAME_PATTERN.matcher(actorDisplayName).matches()) {
                    throw new IllegalArgumentException("Attributed rallies require a public Minecraft name");
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

    enum ResponseKind {
        JOINING,
        UNAVAILABLE;

        static ResponseKind fromWireValue(String value) {
            return switch (value) {
                case "joining" -> JOINING;
                case "unavailable" -> UNAVAILABLE;
                default -> throw new IllegalArgumentException("Unsupported rally response: " + value);
            };
        }
    }

    record Response(
            UUID id,
            UUID rallyId,
            UUID targetPlayerId,
            UUID responderPlayerId,
            String responderDisplayName,
            ResponseKind response,
            String gamemode) {
        public Response {
            if (id == null || rallyId == null || targetPlayerId == null || responderPlayerId == null
                    || responderDisplayName == null || response == null || gamemode == null) {
                throw new IllegalArgumentException("Rally response fields are required");
            }
            gamemode = gamemode.toLowerCase(Locale.ROOT);
            if (!SUPPORTED_GAMEMODES.contains(gamemode) && !NETWORK_GAMEMODE.equals(gamemode)) {
                throw new IllegalArgumentException("Unsupported response gamemode: " + gamemode);
            }
        }
    }

    EnqueueResult enqueue(Request request);

    boolean cancel(UUID outboxId);

    List<Response> pendingResponses(int limit);

    boolean markResponseDelivered(UUID responseId);
}
