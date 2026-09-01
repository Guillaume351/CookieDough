package com.cookiebuild.cookiedough.admin.moderation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/**
 * Native SQL is intentional: a missing additive admin migration must not make
 * Hibernate schema validation prevent Paper from starting.
 */
public final class PostgresModerationRepository implements ModerationRepository {
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000L;
    private final Logger logger;
    private final AtomicLong lastFailureLog = new AtomicLong();

    public PostgresModerationRepository(Logger logger) {
        this.logger = logger;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ModerationAction> findActive(UUID playerId) {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            List<Object[]> rows = entityManager.createNativeQuery("""
                    select id, player_id, player_name, action_type, reason, actor_id,
                           actor_display_name, starts_at, expires_at, source_command_id,
                           metadata::text, created_at
                      from moderation_actions
                     where player_id = :playerId
                       and starts_at <= now()
                       and revoked_at is null
                       and (expires_at is null or expires_at > now())
                     order by created_at desc
                    """).setParameter("playerId", playerId).getResultList();
            List<ModerationAction> actions = new ArrayList<>(rows.size());
            for (Object[] row : rows) actions.add(map(row));
            return actions;
        } catch (RuntimeException error) {
            logUnavailable(error);
            return List.of();
        }
    }

    @Override
    public ModerationAction create(ModerationAction action) {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                int inserted = entityManager.createNativeQuery("""
                        insert into moderation_actions
                            (id, player_id, player_name, action_type, reason, actor_id,
                             actor_display_name, starts_at, expires_at, source_command_id,
                             metadata, created_at)
                        values
                            (:id, :playerId, :playerName, :actionType, :reason, :actorId,
                             :actorDisplayName, :startsAt, :expiresAt, :sourceCommandId,
                             cast(:metadata as jsonb), :createdAt)
                        on conflict (source_command_id) do nothing
                        """)
                        .setParameter("id", action.id())
                        .setParameter("playerId", action.playerId())
                        .setParameter("playerName", action.playerName())
                        .setParameter("actionType", action.type().wireValue())
                        .setParameter("reason", action.reason())
                        .setParameter("actorId", action.actorId())
                        .setParameter("actorDisplayName", action.actorDisplayName())
                        .setParameter("startsAt", action.startsAt())
                        .setParameter("expiresAt", action.expiresAt())
                        .setParameter("sourceCommandId", action.sourceCommandId())
                        .setParameter("metadata", action.metadataJson())
                        .setParameter("createdAt", action.createdAt())
                        .executeUpdate();
                ModerationAction persisted = action;
                if (inserted == 0 && action.sourceCommandId() != null) {
                    @SuppressWarnings("unchecked")
                    List<Object[]> existing = entityManager.createNativeQuery("""
                            select id, player_id, player_name, action_type, reason, actor_id,
                                   actor_display_name, starts_at, expires_at, source_command_id,
                                   metadata::text, created_at
                              from moderation_actions where source_command_id = :sourceCommandId
                            """).setParameter("sourceCommandId", action.sourceCommandId())
                            .setMaxResults(1).getResultList();
                    if (existing.isEmpty()) {
                        throw new IllegalStateException("Moderation idempotency conflict has no persisted action");
                    }
                    persisted = map(existing.getFirst());
                }
                transaction.commit();
                return persisted;
            } catch (RuntimeException error) {
                if (transaction.isActive()) transaction.rollback();
                throw error;
            }
        } catch (RuntimeException error) {
            logUnavailable(error);
            throw new ModerationRepositoryException("Could not persist moderation action", error);
        }
    }

    @Override
    public int revoke(UUID playerId, ModerationAction.Type type, String revokedBy) {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                int updated = entityManager.createNativeQuery("""
                        update moderation_actions
                           set revoked_at = now(), revoked_by = :revokedBy
                         where player_id = :playerId
                           and action_type = :actionType
                           and revoked_at is null
                           and (expires_at is null or expires_at > now())
                        """)
                        .setParameter("revokedBy", revokedBy)
                        .setParameter("playerId", playerId)
                        .setParameter("actionType", type.wireValue())
                        .executeUpdate();
                transaction.commit();
                return updated;
            } catch (RuntimeException error) {
                if (transaction.isActive()) transaction.rollback();
                throw error;
            }
        } catch (RuntimeException error) {
            logUnavailable(error);
            throw new ModerationRepositoryException("Could not revoke moderation action", error);
        }
    }

    private static ModerationAction map(Object[] row) {
        return new ModerationAction(
                (UUID) row[0], (UUID) row[1], (String) row[2],
                ModerationAction.Type.fromWire((String) row[3]), (String) row[4],
                row[5] == null ? null : row[5].toString(), (String) row[6], instant(row[7]), instant(row[8]),
                (UUID) row[9], row[10] == null ? "{}" : row[10].toString(), instant(row[11]));
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return Instant.parse(value.toString());
    }

    private void logUnavailable(RuntimeException error) {
        long now = System.currentTimeMillis();
        long previous = lastFailureLog.get();
        if (now - previous >= FAILURE_LOG_INTERVAL_MS && lastFailureLog.compareAndSet(previous, now)) {
            logger.warning("Admin moderation persistence is unavailable; login remains fail-open until the table is available: "
                    + rootMessage(error));
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        String message = root.getMessage();
        if (message == null || message.isBlank()) return root.getClass().getSimpleName();
        message = message.replaceAll("[\\r\\n\\t]", " ");
        return message.length() <= 240 ? message : message.substring(0, 240);
    }
}
