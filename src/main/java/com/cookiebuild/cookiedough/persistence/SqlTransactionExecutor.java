package com.cookiebuild.cookiedough.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import org.hibernate.Session;

import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/** Gives dependent plugins transaction-scoped JDBC without exposing credentials or a second pool. */
public final class SqlTransactionExecutor {
    @FunctionalInterface
    public interface Work<T> {
        T execute(Connection connection) throws SQLException;
    }

    private SqlTransactionExecutor() {
    }

    public static <T> T inTransaction(Work<T> work) {
        if (work == null) throw new IllegalArgumentException("work is required");
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                T result = entityManager.unwrap(Session.class).doReturningWork(connection -> {
                    applyRuntimeTimeouts(connection);
                    return work.execute(connection);
                });
                transaction.commit();
                return result;
            } catch (RuntimeException error) {
                if (transaction.isActive()) transaction.rollback();
                throw error;
            }
        }
    }

    /**
     * Prevents an asynchronous player action from waiting forever behind a
     * database lock. SET LOCAL scopes both limits to this transaction only.
     */
    private static void applyRuntimeTimeouts(Connection connection) throws SQLException {
        try (var lockTimeout = connection.prepareStatement("SET LOCAL lock_timeout = '3s'");
                var statementTimeout = connection.prepareStatement("SET LOCAL statement_timeout = '7s'")) {
            lockTimeout.execute();
            statementTimeout.execute();
        }
    }
}
