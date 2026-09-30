package com.cookiebuild.cookiedough.retention;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

import com.cookiebuild.cookiedough.model.CoinTransaction;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;

/**
 * Login calendar persistence in {@code player_login_rewards} (created by the
 * website drizzle migration). The calendar row, the coin balance and the coin
 * ledger change in one transaction serialized on the player row.
 */
public final class PostgresLoginRewardRepository {
    public record Outcome(LoginCalendarPolicy.Claim claim, int calendarCoins, int welcomeBackCoins) {
    }

    public Optional<LoginCalendarPolicy.State> load(UUID playerId) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                    select last_claim_day, streak, best_streak, total_claims
                      from player_login_rewards where player_id = :playerId
                    """).setParameter("playerId", playerId).getResultList();
            return rows.isEmpty() ? Optional.empty() : Optional.of(state(rows.get(0)));
        }
    }

    /**
     * Claims today's calendar reward (and the welcome-back gift) at most once.
     *
     * @param extraDaySevenCoins decides extra day-7 coins from the claim (e.g.
     *        when the streak cosmetic is already owned); evaluated inside the
     *        transaction, before any write
     * @return empty when already claimed today or when the player row is missing
     */
    public Optional<Outcome> claim(UUID playerId, LocalDate today, Instant sessionStartedAt,
            IntUnaryOperator extraDaySevenCoins) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                PlayerData player = em.find(PlayerData.class, playerId, LockModeType.PESSIMISTIC_WRITE);
                if (player == null) {
                    tx.rollback();
                    return Optional.empty();
                }
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createNativeQuery("""
                        select last_claim_day, streak, best_streak, total_claims
                          from player_login_rewards where player_id = :playerId for update
                        """).setParameter("playerId", playerId).getResultList();
                LoginCalendarPolicy.State previous = rows.isEmpty() ? null : state(rows.get(0));
                LocalDate lastVisit = previous == null ? previousSessionDay(em, playerId, sessionStartedAt) : null;
                Optional<LoginCalendarPolicy.Claim> evaluated = LoginCalendarPolicy.evaluate(previous, lastVisit, today);
                if (evaluated.isEmpty()) {
                    tx.rollback();
                    return Optional.empty();
                }
                LoginCalendarPolicy.Claim claim = evaluated.get();
                int calendarCoins = claim.coins() + (claim.daySeven()
                        ? Math.max(0, extraDaySevenCoins.applyAsInt(claim.cycleDay())) : 0);
                LoginCalendarPolicy.State next = claim.next(previous);
                em.createNativeQuery("""
                        insert into player_login_rewards
                          (player_id, last_claim_day, streak, best_streak, total_claims, updated_at)
                        values (:playerId, :day, :streak, :best, :total, now())
                        on conflict (player_id) do update set
                          last_claim_day = excluded.last_claim_day,
                          streak = excluded.streak,
                          best_streak = excluded.best_streak,
                          total_claims = excluded.total_claims,
                          updated_at = now()
                        """)
                        .setParameter("playerId", playerId)
                        .setParameter("day", Date.valueOf(next.lastClaimDay()))
                        .setParameter("streak", next.streak())
                        .setParameter("best", next.bestStreak())
                        .setParameter("total", next.totalClaims())
                        .executeUpdate();
                java.util.Date now = new java.util.Date();
                player.addCoins(calendarCoins);
                em.persist(new CoinTransaction(player, calendarCoins, "login-calendar:" + today, now));
                if (claim.welcomeBack()) {
                    player.addCoins(claim.welcomeBackCoins());
                    em.persist(new CoinTransaction(player, claim.welcomeBackCoins(), "welcome-back:" + today, now));
                }
                tx.commit();
                return Optional.of(new Outcome(claim, calendarCoins, claim.welcomeBackCoins()));
            } catch (RuntimeException error) {
                if (tx.isActive()) tx.rollback();
                throw error;
            }
        }
    }

    /** Last Paris day of a session that started before this one (legacy players without a calendar row). */
    private static LocalDate previousSessionDay(EntityManager em, UUID playerId, Instant sessionStartedAt) {
        Object value = em.createNativeQuery("""
                select max(start_time) from player_sessions
                 where player_id = :playerId and start_time < :cutoff
                """)
                .setParameter("playerId", playerId)
                .setParameter("cutoff", Timestamp.from(sessionStartedAt.minusSeconds(60)))
                .getSingleResult();
        Instant instant = switch (value) {
            case null -> null;
            case Timestamp timestamp -> timestamp.toInstant();
            case java.util.Date date -> date.toInstant();
            case java.time.LocalDateTime local -> local.atZone(java.time.ZoneId.systemDefault()).toInstant();
            case java.time.OffsetDateTime offset -> offset.toInstant();
            case Instant raw -> raw;
            default -> null;
        };
        return instant == null ? null : ParisCalendar.dayOf(instant);
    }

    private static LoginCalendarPolicy.State state(Object[] row) {
        LocalDate day = row[0] instanceof LocalDate local ? local
                : row[0] instanceof Date date ? date.toLocalDate() : LocalDate.parse(String.valueOf(row[0]));
        return new LoginCalendarPolicy.State(day, ((Number) row[1]).intValue(), ((Number) row[2]).intValue(),
                ((Number) row[3]).intValue());
    }
}
