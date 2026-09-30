package com.cookiebuild.cookiedough.retention;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.cookiebuild.cookiedough.cosmetics.CosmeticCatalog;
import com.cookiebuild.cookiedough.cosmetics.CosmeticDefinition;
import com.cookiebuild.cookiedough.model.CoinTransaction;
import com.cookiebuild.cookiedough.model.MinigameProgression;
import com.cookiebuild.cookiedough.model.MinigameProgressionId;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TemporalType;

/**
 * Game-server side of the {@code player_reward_grants} contract: the website
 * only inserts rows; CookieDough credits undelivered rows of online players
 * and stamps {@code delivered_at} in the same transaction.
 */
public final class PostgresRewardGrantRepository {
    /** XP from grants is kept on its own progression row so every mode stays untouched. */
    public static final String REWARD_PROGRESSION_KEY = "rewards";

    public record Grant(long id, UUID playerId, String source, String periodKey, int coins, int xp,
            String cosmeticId) {
    }

    public List<Grant> deliverPending(Collection<UUID> players, int limit, Consumer<String> warn) {
        if (players.isEmpty()) return List.of();
        List<Long> ids;
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            ids = em.createNativeQuery("""
                    select id from player_reward_grants
                     where delivered_at is null and player_uuid in (:players)
                     order by id
                     limit :limit
                    """, Long.class)
                    .setParameter("players", List.copyOf(players))
                    .setParameter("limit", limit)
                    .getResultList();
        }
        List<Grant> delivered = new ArrayList<>();
        for (Long id : ids) {
            try {
                Grant grant = deliver(id);
                if (grant != null) delivered.add(grant);
            } catch (RuntimeException error) {
                warn.accept("Could not deliver reward grant " + id + ": " + rootMessage(error));
            }
        }
        return List.copyOf(delivered);
    }

    private Grant deliver(long id) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createNativeQuery("""
                        select id, player_uuid, source, period_key, coins, xp, cosmetic_id
                          from player_reward_grants
                         where id = :id and delivered_at is null
                         for update skip locked
                        """).setParameter("id", id).getResultList();
                if (rows.isEmpty()) {
                    tx.rollback();
                    return null;
                }
                Object[] row = rows.get(0);
                UUID playerId = row[1] instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(row[1]));
                Grant grant = new Grant(((Number) row[0]).longValue(), playerId, String.valueOf(row[2]),
                        String.valueOf(row[3]), ((Number) row[4]).intValue(), ((Number) row[5]).intValue(),
                        row[6] == null ? null : String.valueOf(row[6]));
                CosmeticDefinition cosmetic = null;
                if (grant.cosmeticId() != null && !grant.cosmeticId().isBlank()) {
                    cosmetic = CosmeticCatalog.find(grant.cosmeticId()).orElse(null);
                    if (cosmetic == null) {
                        // Newer website catalog than this plugin: keep the row for a later release.
                        throw new IllegalStateException("unknown cosmetic " + grant.cosmeticId());
                    }
                }
                PlayerData player = em.find(PlayerData.class, playerId, LockModeType.PESSIMISTIC_WRITE);
                if (player == null) throw new IllegalStateException("player row missing");
                Date now = new Date();
                if (grant.coins() > 0) {
                    player.addCoins(grant.coins());
                    em.persist(new CoinTransaction(player, grant.coins(),
                            "reward-grant:" + grant.source() + ":" + grant.periodKey(), now));
                }
                if (grant.xp() > 0) {
                    MinigameProgression progression = em.find(MinigameProgression.class,
                            new MinigameProgressionId(playerId, REWARD_PROGRESSION_KEY), LockModeType.PESSIMISTIC_WRITE);
                    if (progression == null) {
                        progression = new MinigameProgression(playerId, REWARD_PROGRESSION_KEY);
                        em.persist(progression);
                    }
                    progression.addExperience(grant.xp());
                }
                if (cosmetic != null) {
                    em.createNativeQuery("""
                            insert into cosmetic_entitlements
                              (player_id, cosmetic_id, source, granted_at, revoked_at, expires_at)
                            values (:playerId, :cosmeticId, :source, :grantedAt, null, null)
                            on conflict (player_id, cosmetic_id, source) do nothing
                            """)
                            .setParameter("playerId", playerId)
                            .setParameter("cosmeticId", cosmetic.id())
                            .setParameter("source", "reward-grant:" + grant.id())
                            .setParameter("grantedAt", now, TemporalType.TIMESTAMP)
                            .executeUpdate();
                    if (cosmetic.selectionRequired()) {
                        em.createNativeQuery("""
                                insert into cosmetic_selections (player_id, slot, cosmetic_id, selected_at)
                                values (:playerId, :slot, :cosmeticId, :selectedAt)
                                on conflict (player_id, slot) do nothing
                                """)
                                .setParameter("playerId", playerId)
                                .setParameter("slot", cosmetic.slot().name())
                                .setParameter("cosmeticId", cosmetic.id())
                                .setParameter("selectedAt", now, TemporalType.TIMESTAMP)
                                .executeUpdate();
                    }
                }
                em.createNativeQuery("update player_reward_grants set delivered_at = now() where id = :id")
                        .setParameter("id", grant.id())
                        .executeUpdate();
                tx.commit();
                return grant;
            } catch (RuntimeException error) {
                if (tx.isActive()) tx.rollback();
                throw error;
            }
        }
    }

    static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
