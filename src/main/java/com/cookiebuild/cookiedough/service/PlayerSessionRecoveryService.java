package com.cookiebuild.cookiedough.service;

import java.util.Date;
import java.util.List;

import com.cookiebuild.cookiedough.model.PlayerSession;
import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;

/** Closes sessions left open by an unclean single-server restart. */
public final class PlayerSessionRecoveryService {

    public int recoverOpenSessions(Date recoveredAt) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            try {
                transaction.begin();
                List<PlayerSession> sessions = em.createQuery(
                                "SELECT session FROM PlayerSession session WHERE session.endTime IS NULL",
                                PlayerSession.class)
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .getResultList();
                int recovered = finalizeSessions(sessions, recoveredAt);
                transaction.commit();
                return recovered;
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    static int finalizeSessions(List<PlayerSession> sessions, Date recoveredAt) {
        int recovered = 0;
        for (PlayerSession session : sessions) {
            if (session.getEndTime() != null) {
                continue;
            }
            long checkpointedDuration = checkpointedDuration(session, recoveredAt);
            session.setEndTime(checkpointedEndTime(session.getStartTime(), checkpointedDuration, recoveredAt));
            session.setDuration(checkpointedDuration);
            // Keep the crash marker true: this was not a graceful player quit.
            session.setServerCrash(true);
            recovered += 1;
        }
        return recovered;
    }

    static long durationMillis(Date start, Date end) {
        if (start == null || end == null) {
            return 0L;
        }
        return Math.max(0L, end.getTime() - start.getTime());
    }

    /**
     * Uses the last duration persisted while the server was alive. The restart
     * instant is only an upper bound and must never be counted as play time.
     */
    static long checkpointedDuration(PlayerSession session, Date recoveredAt) {
        long maximumDuration = durationMillis(session.getStartTime(), recoveredAt);
        Long persistedDuration = session.getDuration();
        if (persistedDuration == null) {
            return 0L;
        }
        return Math.min(Math.max(0L, persistedDuration), maximumDuration);
    }

    static Date checkpointedEndTime(Date start, long duration, Date recoveredAt) {
        if (start == null) {
            return recoveredAt;
        }
        long recoveredAtMillis = recoveredAt == null ? start.getTime() : recoveredAt.getTime();
        long endMillis;
        try {
            endMillis = Math.addExact(start.getTime(), Math.max(0L, duration));
        } catch (ArithmeticException overflow) {
            endMillis = recoveredAtMillis;
        }
        return new Date(Math.min(endMillis, recoveredAtMillis));
    }
}
