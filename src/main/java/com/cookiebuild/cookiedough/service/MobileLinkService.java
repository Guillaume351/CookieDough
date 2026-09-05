package com.cookiebuild.cookiedough.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/** Persists secure, purpose-scoped player-link challenges. */
public final class MobileLinkService {
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);
    private static final Duration CHALLENGE_RETENTION = Duration.ofDays(7);
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;

    private final SecureRandom random;
    private final String pepper;

    public enum Purpose {
        MOBILE_LINK("mobile_link"),
        COMMERCE_SESSION("commerce_session");

        private final String wireValue;

        Purpose(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public MobileLinkService(String pepper) {
        this(pepper, new SecureRandom());
    }

    MobileLinkService(String pepper, SecureRandom random) {
        if (!isValidPepper(pepper)) {
            throw new IllegalArgumentException("MOBILE_LINK_PEPPER must contain at least 32 characters");
        }
        this.pepper = pepper;
        this.random = random;
    }

    /**
     * Creates a new challenge and atomically invalidates any older outstanding
     * challenge for the same player and purpose.
     */
    public LinkChallenge createChallenge(UUID playerId, String edition) {
        return createChallenge(playerId, edition, Purpose.MOBILE_LINK);
    }

    public LinkChallenge createChallenge(UUID playerId, String edition, Purpose purpose) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(purpose, "purpose");
        Instant now = Instant.now();
        Instant expiresAt = now.plus(CHALLENGE_TTL);
        String normalizedEdition = normalizeEdition(edition);

        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                lockPlayer(entityManager, playerId);
                entityManager.createQuery("""
                        update PlayerLinkChallenge challenge
                           set challenge.consumedAt = :now
                         where challenge.playerId = :playerId
                           and challenge.purpose = :purpose
                           and challenge.consumedAt is null
                        """)
                        .setParameter("now", now)
                        .setParameter("playerId", playerId)
                        .setParameter("purpose", purpose.wireValue())
                        .executeUpdate();
                entityManager.createQuery("""
                        delete from PlayerLinkChallenge challenge
                         where challenge.consumedAt is not null
                           and challenge.createdAt < :cutoff
                        """)
                        .setParameter("cutoff", now.minus(CHALLENGE_RETENTION))
                        .executeUpdate();
                String code = generateUniqueCode(random, candidate -> entityManager.createNativeQuery("""
                        insert into player_link_challenges
                          (id, player_id, edition, purpose, code_hmac, expires_at, created_at)
                        values (:id, :playerId, :edition, :purpose, :codeHmac, :expiresAt, :createdAt)
                        on conflict (code_hmac) do nothing
                        """)
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("playerId", playerId)
                        .setParameter("edition", normalizedEdition)
                        .setParameter("purpose", purpose.wireValue())
                        .setParameter("codeHmac", hmacHex(candidate, pepper))
                        .setParameter("expiresAt", expiresAt)
                        .setParameter("createdAt", now)
                        .executeUpdate() == 1);
                transaction.commit();
                return new LinkChallenge(code, expiresAt, purpose);
            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw exception;
            }
        }
    }

    public boolean hasActiveLink(UUID playerId) {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            Number count = (Number) entityManager.createNativeQuery("""
                    select count(*)
                      from mobile_player_links
                     where player_id = :playerId
                       and revoked_at is null
                    """)
                    .setParameter("playerId", playerId)
                    .getSingleResult();
            return count.longValue() > 0;
        }
    }

    /** Revokes active app links and outstanding challenges for a player. */
    public boolean revoke(UUID playerId) {
        Instant now = Instant.now();
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                lockPlayer(entityManager, playerId);
                @SuppressWarnings("unchecked")
                java.util.List<String> affectedUsers = entityManager.createNativeQuery("""
                        select distinct firebase_uid
                          from mobile_player_links
                         where player_id = :playerId
                           and revoked_at is null
                        """)
                        .setParameter("playerId", playerId)
                        .getResultList();
                int links = entityManager.createNativeQuery("""
                        update mobile_player_links
                           set revoked_at = :now,
                               is_primary = false
                         where player_id = :playerId
                           and revoked_at is null
                        """)
                        .setParameter("now", now)
                        .setParameter("playerId", playerId)
                        .executeUpdate();
                for (String firebaseUid : affectedUsers) {
                    entityManager.createNativeQuery("""
                            update mobile_player_links candidate
                               set is_primary = true
                             where (candidate.firebase_uid, candidate.player_id, candidate.edition) = (
                                   select link.firebase_uid, link.player_id, link.edition
                                     from mobile_player_links link
                                    where link.firebase_uid = :firebaseUid
                                      and link.revoked_at is null
                                    order by link.linked_at asc
                                    limit 1
                             )
                               and not exists (
                                   select 1
                                     from mobile_player_links active_primary
                                    where active_primary.firebase_uid = :firebaseUid
                                      and active_primary.revoked_at is null
                                      and active_primary.is_primary
                               )
                            """)
                            .setParameter("firebaseUid", firebaseUid)
                            .executeUpdate();
                }
                entityManager.createQuery("""
                        update PlayerLinkChallenge challenge
                           set challenge.consumedAt = :now
                         where challenge.playerId = :playerId
                           and challenge.purpose = :purpose
                           and challenge.consumedAt is null
                        """)
                        .setParameter("now", now)
                        .setParameter("playerId", playerId)
                        .setParameter("purpose", Purpose.MOBILE_LINK.wireValue())
                        .executeUpdate();
                transaction.commit();
                return links > 0;
            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw exception;
            }
        }
    }

    /** Expires abandoned codes and removes old challenge audit rows. */
    public int pruneExpiredChallenges() {
        Instant now = Instant.now();
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                int expired = entityManager.createQuery("""
                        update PlayerLinkChallenge challenge
                           set challenge.consumedAt = :now
                         where challenge.consumedAt is null
                           and challenge.expiresAt < :now
                        """)
                        .setParameter("now", now)
                        .executeUpdate();
                entityManager.createQuery("""
                        delete from PlayerLinkChallenge challenge
                         where challenge.consumedAt is not null
                           and challenge.createdAt < :cutoff
                        """)
                        .setParameter("cutoff", now.minus(CHALLENGE_RETENTION))
                        .executeUpdate();
                transaction.commit();
                return expired;
            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw exception;
            }
        }
    }

    static String generateCode(SecureRandom random) {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    static String generateUniqueCode(SecureRandom random, Predicate<String> reserve) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = generateCode(random);
            if (reserve.test(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not reserve a unique player-link code");
    }

    static String hmacHex(String code, String pepper) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.toUpperCase(Locale.ROOT)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    public static boolean isValidPepper(String pepper) {
        if (pepper == null || pepper.length() < 32) {
            return false;
        }
        String normalized = pepper.toLowerCase(Locale.ROOT);
        return !normalized.contains("replace-with")
                && !normalized.contains("change-me")
                && !normalized.contains("your-secret")
                && !normalized.contains("example");
    }

    private static void lockPlayer(EntityManager entityManager, UUID playerId) {
        entityManager.createNativeQuery("select id from playerdata where id = :playerId for update")
                .setParameter("playerId", playerId)
                .getSingleResult();
    }

    private static String normalizeEdition(String edition) {
        return "bedrock".equalsIgnoreCase(edition) ? "bedrock" : "java";
    }

    public record LinkChallenge(String code, Instant expiresAt, Purpose purpose) {
    }
}
