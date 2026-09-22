package com.cookiebuild.cookiedough.service;

import java.util.UUID;

import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/** Atomically caps the app reminder at three impressions, at least 30 days apart. */
public final class MobilePromotionService {
    public boolean claimPromotion(UUID playerId) {
        try (EntityManager entityManager = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                int claimed = entityManager.createNativeQuery("""
                        insert into player_app_promotion_state (player_id, last_shown_at, show_count)
                        select :playerId, now(), 1
                         where not exists (
                           select 1 from mobile_player_links link
                            where link.player_id = :playerId and link.revoked_at is null
                         )
                        on conflict (player_id) do update
                          set last_shown_at = now(),
                              show_count = player_app_promotion_state.show_count + 1
                        where player_app_promotion_state.show_count < 3
                          and player_app_promotion_state.last_shown_at <= now() - interval '30 days'
                          and not exists (
                            select 1 from mobile_player_links link
                             where link.player_id = :playerId and link.revoked_at is null
                          )
                        """)
                        .setParameter("playerId", playerId)
                        .executeUpdate();
                transaction.commit();
                return claimed > 0;
            } catch (RuntimeException error) {
                if (transaction.isActive()) transaction.rollback();
                throw error;
            }
        }
    }
}
