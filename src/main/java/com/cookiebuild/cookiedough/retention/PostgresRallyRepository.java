package com.cookiebuild.cookiedough.retention;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.Session;

import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/** Inserts structured rallies into the existing mobile notification outbox. */
public final class PostgresRallyRepository implements RallyRepository {
    public static final int DELIVERY_GRACE_SECONDS = 15;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GLOBAL_LOCK_KEY = "cookiebuild:player-rally";

    @Override
    public EnqueueResult enqueue(Request request) {
        String payload = payload(request);
        return transaction("enqueue player rally", connection -> {
            advisoryLock(connection);
            if (recent(connection, "created_at > now() - interval '90 seconds'", List.of())) {
                return EnqueueResult.rejected(EnqueueStatus.GLOBAL_COOLDOWN);
            }
            if (request.source() == Source.PLAYER && recent(connection, """
                    created_at > now() - interval '15 minutes'
                    AND payload ->> 'source' = 'player'
                    AND payload ->> 'gamemode' = ?
                    AND lower(payload ->> 'actorDisplayName') = lower(?)
                    """, List.of(request.gamemode(), request.actorDisplayName()))) {
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
                    INSERT INTO mobile_notification_outbox
                        (id, kind, audience, payload, status, attempts, available_at, created_at)
                    VALUES (?, 'player_rally', '{"all":true}'::jsonb, ?::jsonb,
                            'pending', 0, now() + interval '15 seconds', now())
                    ON CONFLICT DO NOTHING
                    RETURNING available_at
                    """)) {
                statement.setObject(1, outboxId);
                statement.setString(2, payload);
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
