package com.cookiebuild.cookiedough.retention;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;

public final class PostgresChangelogRepository implements ChangelogRepository {
    private static final String UNREAD_FILTER = """
            FROM mobile_news_posts news
            LEFT JOIN player_changelog_state state ON state.player_id = :playerId
            WHERE news.status = 'published'
              AND news.published_at IS NOT NULL
              AND news.published_at <= now()
              AND (news.expires_at IS NULL OR news.expires_at > now())
              AND (
                state.last_seen_published_at IS NULL
                OR (news.published_at, news.id) > (state.last_seen_published_at, state.last_seen_post_id)
              )
            """;

    @Override
    public ChangelogDigest findUnread(UUID playerId) {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery("""
                    SELECT news.id, news.title, news.summary, news.published_at
                    """ + UNREAD_FILTER + """
                    ORDER BY news.published_at DESC, news.id DESC
                    """)
                    .setParameter("playerId", playerId)
                    .getResultList();

            if (rows.isEmpty()) {
                return ChangelogDigest.empty();
            }

            List<ChangelogEntry> entries = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                entries.add(new ChangelogEntry(
                        uuid(row[0]),
                        String.valueOf(row[1]),
                        String.valueOf(row[2]),
                        instant(row[3])));
            }
            ChangelogEntry newest = entries.getFirst();
            return new ChangelogDigest(entries, entries.size(), newest.publishedAt(), newest.id());
        }
    }

    @Override
    public void acknowledge(UUID playerId, Instant publishedAt, UUID postId) {
        if (publishedAt == null || postId == null) {
            return;
        }
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                Query query = entityManager.createNativeQuery("""
                        INSERT INTO player_changelog_state (
                          player_id, last_seen_published_at, last_seen_post_id, updated_at
                        ) VALUES (
                          :playerId, :publishedAt, :postId, now()
                        )
                        ON CONFLICT (player_id) DO UPDATE SET
                          last_seen_published_at = EXCLUDED.last_seen_published_at,
                          last_seen_post_id = EXCLUDED.last_seen_post_id,
                          updated_at = now()
                        WHERE player_changelog_state.last_seen_published_at IS NULL
                           OR (EXCLUDED.last_seen_published_at, EXCLUDED.last_seen_post_id)
                              > (player_changelog_state.last_seen_published_at, player_changelog_state.last_seen_post_id)
                        """);
                query.setParameter("playerId", playerId);
                query.setParameter("publishedAt", OffsetDateTime.ofInstant(publishedAt, ZoneOffset.UTC));
                query.setParameter("postId", postId);
                query.executeUpdate();
                transaction.commit();
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Date date) return date.toInstant();
        throw new IllegalArgumentException("Unsupported changelog timestamp: " + value);
    }
}
