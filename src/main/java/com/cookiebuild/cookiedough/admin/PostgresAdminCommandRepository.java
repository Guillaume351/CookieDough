package com.cookiebuild.cookiedough.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/** Best-effort command status mirroring; RabbitMQ results remain authoritative. */
final class PostgresAdminCommandRepository implements AdminCommandRepository {
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000L;
    private final ObjectMapper mapper;
    private final Logger logger;
    private final AtomicLong lastFailureLog = new AtomicLong();

    PostgresAdminCommandRepository(ObjectMapper mapper, Logger logger) {
        this.mapper = mapper;
        this.logger = logger;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<AdminCommandResult> findTerminal(UUID commandId) {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            List<Object[]> rows = entityManager.createNativeQuery("""
                    select status, coalesce(result, '{}'::jsonb)::text, error
                      from admin_commands
                     where id = :id
                       and status in ('succeeded', 'failed', 'expired')
                    """).setParameter("id", commandId).setMaxResults(1).getResultList();
            if (rows.isEmpty()) return Optional.empty();
            Object[] row = rows.getFirst();
            ObjectNode result = (ObjectNode) mapper.readTree(row[1].toString());
            return Optional.of(new AdminCommandResult(toWireStatus((String) row[0]), result,
                    row[2] == null ? null : row[2].toString()));
        } catch (RuntimeException error) {
            logUnavailable(error);
            return Optional.empty();
        } catch (Exception error) {
            logger.warning("Could not decode a terminal admin command result: " + error.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void recordStarted(UUID commandId) {
        update("""
                update admin_commands
                   set status = 'running', started_at = coalesce(started_at, now()), error = null
                 where id = :id and status not in ('succeeded', 'failed', 'expired', 'cancelled')
                """, commandId, null, null);
    }

    @Override
    public void recordCompleted(UUID commandId, AdminCommandResult result) {
        update("""
                update admin_commands
                   set status = :status, completed_at = now(), result = cast(:result as jsonb), error = :error
                 where id = :id
                """, commandId, result, toDatabaseStatus(result.status()));
    }

    private void update(String sql, UUID commandId, AdminCommandResult result, String databaseStatus) {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                var query = entityManager.createNativeQuery(sql).setParameter("id", commandId);
                if (result != null) {
                    query.setParameter("status", databaseStatus);
                    query.setParameter("result", result.result().toString());
                    query.setParameter("error", result.error());
                }
                query.executeUpdate();
                transaction.commit();
            } catch (RuntimeException error) {
                if (transaction.isActive()) transaction.rollback();
                throw error;
            }
        } catch (RuntimeException error) {
            logUnavailable(error);
        }
    }

    static String toDatabaseStatus(String wireStatus) {
        return "completed".equals(wireStatus) ? "succeeded" : wireStatus;
    }

    static String toWireStatus(String databaseStatus) {
        return "succeeded".equals(databaseStatus) ? "completed" : databaseStatus;
    }

    private void logUnavailable(RuntimeException error) {
        long now = System.currentTimeMillis();
        long previous = lastFailureLog.get();
        if (now - previous >= FAILURE_LOG_INTERVAL_MS && lastFailureLog.compareAndSet(previous, now)) {
            logger.warning("Admin command status persistence is unavailable; RabbitMQ processing will continue: "
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
