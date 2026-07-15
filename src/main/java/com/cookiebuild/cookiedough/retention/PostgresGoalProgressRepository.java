package com.cookiebuild.cookiedough.retention;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/** Shared PostgreSQL persistence for the in-game and mobile goal views. */
public final class PostgresGoalProgressRepository {
    private static final ObjectMapper JSON = new ObjectMapper();

    public Map<UUID, GoalProgressSnapshot> loadAll() {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery("""
                    select player_id, day, daily_matches, daily_wins, first_win_date,
                           week, weekly_matches, weekly_wins, weekly_kills, achievements::text
                      from player_goal_progress
                    """).getResultList();
            Map<UUID, GoalProgressSnapshot> snapshots = new HashMap<>();
            for (Object[] row : rows) {
                UUID playerId = row[0] instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(row[0]));
                snapshots.put(playerId, new GoalProgressSnapshot(
                        localDate(row[1]), number(row[2]), number(row[3]), nullableDate(row[4]),
                        String.valueOf(row[5]), number(row[6]), number(row[7]), number(row[8]),
                        achievements(String.valueOf(row[9]))));
            }
            return snapshots;
        }
    }

    public void upsert(UUID playerId, GoalProgressSnapshot snapshot) {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                entityManager.createNativeQuery("""
                        insert into player_goal_progress
                          (player_id, day, daily_matches, daily_wins, first_win_date,
                           week, weekly_matches, weekly_wins, weekly_kills, achievements, updated_at)
                        values
                          (:playerId, :day, :dailyMatches, :dailyWins, :firstWinDate,
                           :week, :weeklyMatches, :weeklyWins, :weeklyKills,
                           cast(:achievements as jsonb), now())
                        on conflict (player_id) do update set
                          day = greatest(player_goal_progress.day, excluded.day),
                          daily_matches = case
                            when excluded.day > player_goal_progress.day then excluded.daily_matches
                            when excluded.day = player_goal_progress.day then greatest(player_goal_progress.daily_matches, excluded.daily_matches)
                            else player_goal_progress.daily_matches end,
                          daily_wins = case
                            when excluded.day > player_goal_progress.day then excluded.daily_wins
                            when excluded.day = player_goal_progress.day then greatest(player_goal_progress.daily_wins, excluded.daily_wins)
                            else player_goal_progress.daily_wins end,
                          first_win_date = greatest(player_goal_progress.first_win_date, excluded.first_win_date),
                          week = greatest(player_goal_progress.week, excluded.week),
                          weekly_matches = case
                            when excluded.week > player_goal_progress.week then excluded.weekly_matches
                            when excluded.week = player_goal_progress.week then greatest(player_goal_progress.weekly_matches, excluded.weekly_matches)
                            else player_goal_progress.weekly_matches end,
                          weekly_wins = case
                            when excluded.week > player_goal_progress.week then excluded.weekly_wins
                            when excluded.week = player_goal_progress.week then greatest(player_goal_progress.weekly_wins, excluded.weekly_wins)
                            else player_goal_progress.weekly_wins end,
                          weekly_kills = case
                            when excluded.week > player_goal_progress.week then excluded.weekly_kills
                            when excluded.week = player_goal_progress.week then greatest(player_goal_progress.weekly_kills, excluded.weekly_kills)
                            else player_goal_progress.weekly_kills end,
                          achievements = (
                            select coalesce(jsonb_agg(value order by value), '[]'::jsonb)
                              from (select distinct value
                                      from jsonb_array_elements(player_goal_progress.achievements || excluded.achievements)) merged
                          ),
                          updated_at = now()
                        """)
                        .setParameter("playerId", playerId)
                        .setParameter("day", Date.valueOf(snapshot.day()))
                        .setParameter("dailyMatches", snapshot.dailyMatches())
                        .setParameter("dailyWins", snapshot.dailyWins())
                        .setParameter("firstWinDate", snapshot.firstWinDate() == null
                                ? null : Date.valueOf(snapshot.firstWinDate()))
                        .setParameter("week", snapshot.week())
                        .setParameter("weeklyMatches", snapshot.weeklyMatches())
                        .setParameter("weeklyWins", snapshot.weeklyWins())
                        .setParameter("weeklyKills", snapshot.weeklyKills())
                        .setParameter("achievements", JSON.writeValueAsString(snapshot.achievements().stream().sorted().toList()))
                        .executeUpdate();
                transaction.commit();
            } catch (Exception error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw new IllegalStateException("Could not persist player goal progress", error);
            }
        }
    }

    private static LocalDate localDate(Object value) {
        LocalDate date = nullableDate(value);
        if (date == null) throw new IllegalStateException("Goal date is missing");
        return date;
    }

    private static LocalDate nullableDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof Date date) return date.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static Set<String> achievements(String json) {
        try {
            return new HashSet<>(JSON.readValue(json, new TypeReference<List<String>>() { }));
        } catch (Exception error) {
            throw new IllegalStateException("Invalid goal achievement data", error);
        }
    }
}
