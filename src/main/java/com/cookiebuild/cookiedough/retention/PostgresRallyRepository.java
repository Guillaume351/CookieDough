package com.cookiebuild.cookiedough.retention;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.hibernate.Session;

import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/** Inserts structured rallies into the existing mobile notification outbox. */
public final class PostgresRallyRepository implements RallyRepository {
    public static final int DELIVERY_GRACE_SECONDS = 3;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GLOBAL_LOCK_KEY = "cookiebuild:player-rally";
    private static final String DEFAULT_SERVER_ID = "minecraft-1";
    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    private final String serverId;

    public PostgresRallyRepository() {
        this(serverId(System.getenv()));
    }

    PostgresRallyRepository(String serverId) {
        this.serverId = serverId(Map.of("ADMIN_BRIDGE_SERVER_ID", serverId));
    }

    @Override
    public EnqueueResult enqueue(Request request) {
        String payload = payload(request);
        return transaction("enqueue player rally", connection -> {
            advisoryLock(connection);
            if (recent(connection, "created_at > now() - interval '90 seconds'", List.of())) {
                return EnqueueResult.rejected(EnqueueStatus.GLOBAL_COOLDOWN);
            }
            if ((request.source() == Source.PLAYER || request.source() == Source.LOGIN)
                    && recent(connection, """
                    created_at > now() - interval '15 minutes'
                    AND payload ->> 'source' = ?
                    AND payload ->> 'gamemode' = ?
                    AND lower(payload ->> 'actorDisplayName') = lower(?)
                    """, List.of(request.source().wireValue(), request.gamemode(), request.actorDisplayName()))) {
                return EnqueueResult.rejected(EnqueueStatus.PLAYER_COOLDOWN);
            }
            if (request.source() == Source.AUTOMATIC && recent(connection, """
                    created_at > now() - interval '30 minutes'
                    AND payload ->> 'source' = 'automatic'
                    AND payload ->> 'gamemode' = ?
                    """, List.of(request.gamemode()))) {
                return EnqueueResult.rejected(EnqueueStatus.AUTOMATIC_COOLDOWN);
            }
            if (recent(connection, """
                    created_at > now() - interval '5 minutes'
                    AND payload ->> 'gamemode' = ?
                    """, List.of(request.gamemode()))) {
                return EnqueueResult.rejected(EnqueueStatus.GAMEMODE_COOLDOWN);
            }

            UUID outboxId = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement("""
                    WITH enqueued AS (
                        INSERT INTO mobile_notification_outbox
                            (id, kind, audience, payload, status, attempts, available_at, created_at)
                        VALUES (?, 'player_rally', '{"all":true}'::jsonb, ?::jsonb,
                                'pending', 0, now() + interval '3 seconds', now())
                        ON CONFLICT DO NOTHING
                        RETURNING id, available_at
                    )
                    INSERT INTO player_rallies
                        (id, outbox_id, server_id, target_player_id, game_id, source, gamemode,
                         available_at, expires_at)
                    SELECT ?, enqueued.id, ?, ?, ?, ?, ?, enqueued.available_at,
                           enqueued.available_at + interval '5 minutes'
                      FROM enqueued
                    RETURNING available_at
                    """)) {
                statement.setObject(1, outboxId);
                statement.setString(2, payload);
                statement.setObject(3, request.rallyId());
                statement.setString(4, serverId);
                statement.setObject(5, request.targetPlayerId());
                if (request.gameId() == null) {
                    statement.setNull(6, Types.OTHER);
                } else {
                    statement.setObject(6, request.gameId());
                }
                statement.setString(7, request.source().wireValue());
                statement.setString(8, request.gamemode());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return EnqueueResult.rejected(EnqueueStatus.DUPLICATE);
                    }
                    return new EnqueueResult(EnqueueStatus.ENQUEUED, outboxId,
                            rows.getObject(1, OffsetDateTime.class).toInstant());
                }
            }
        });
    }

    @Override
    public boolean cancel(UUID outboxId) {
        return transaction("cancel player rally", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM mobile_notification_outbox
                     WHERE id = ? AND kind = 'player_rally' AND status = 'pending'
                    """)) {
                statement.setObject(1, outboxId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    @Override
    public List<Response> pendingResponses(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Response poll limit must be between 1 and 100");
        }
        return transaction("poll player rally responses", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT response.id,
                           response.rally_id,
                           rally.target_player_id,
                           response.responder_player_id,
                           coalesce(player.name, 'Player') AS responder_display_name,
                           response.response,
                           rally.gamemode
                      FROM player_rally_responses response
                      JOIN player_rallies rally ON rally.id = response.rally_id
                      JOIN playerdata player ON player.id = response.responder_player_id
                     WHERE rally.server_id = ?
                       AND response.delivered_at IS NULL
                       AND rally.expires_at > now()
                     ORDER BY response.responded_at, response.id
                     LIMIT ?
                    """)) {
                statement.setString(1, serverId);
                statement.setInt(2, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    java.util.ArrayList<Response> responses = new java.util.ArrayList<>();
                    while (rows.next()) {
                        responses.add(new Response(
                                rows.getObject("id", UUID.class),
                                rows.getObject("rally_id", UUID.class),
                                rows.getObject("target_player_id", UUID.class),
                                rows.getObject("responder_player_id", UUID.class),
                                rows.getString("responder_display_name"),
                                ResponseKind.fromWireValue(rows.getString("response")),
                                rows.getString("gamemode")));
                    }
                    return List.copyOf(responses);
                }
            }
        });
    }

    @Override
    public boolean markResponseDelivered(UUID responseId) {
        return transaction("acknowledge player rally response", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE player_rally_responses
                       SET delivered_at = now()
                     WHERE id = ? AND delivered_at IS NULL
                    """)) {
                statement.setObject(1, responseId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    private static boolean recent(Connection connection, String condition, List<String> parameters)
            throws SQLException {
        String query = "SELECT EXISTS (SELECT 1 FROM mobile_notification_outbox "
                + "WHERE kind = 'player_rally' AND " + condition + ")";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setString(index + 1, parameters.get(index));
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private static void advisoryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, GLOBAL_LOCK_KEY);
            statement.execute();
        }
    }

    static String payload(Request request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("rallyId", request.rallyId().toString());
        payload.put("source", request.source().wireValue());
        payload.put("gamemode", request.gamemode());
        payload.put("edition", "crossplay");
        payload.put("queuedCount", request.queuedCount());
        payload.put("neededCount", request.neededCount());
        payload.put("actorDisplayName", request.actorDisplayName());
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            throw new RallyRepositoryException("Could not serialize player rally", error);
        }
    }

    static String serverId(Map<String, String> environment) {
        String configured = environment.get("ADMIN_BRIDGE_SERVER_ID");
        String value = configured == null || configured.isBlank() ? DEFAULT_SERVER_ID : configured.trim();
        if (!SERVER_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("ADMIN_BRIDGE_SERVER_ID is invalid");
        }
        return value;
    }

    private <T> T transaction(String operation, SqlWork<T> work) {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                T result = entityManager.unwrap(Session.class)
                        .doReturningWork(connection -> work.execute(connection));
                transaction.commit();
                return result;
            } catch (Exception error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                if (error instanceof RallyRepositoryException repositoryError) {
                    throw repositoryError;
                }
                throw new RallyRepositoryException("Could not " + operation, error);
            }
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
