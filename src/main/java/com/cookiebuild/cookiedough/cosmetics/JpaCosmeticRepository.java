package com.cookiebuild.cookiedough.cosmetics;

import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cookiebuild.cookiedough.model.CosmeticEntitlement;
import com.cookiebuild.cookiedough.model.CosmeticSelection;
import com.cookiebuild.cookiedough.model.CosmeticSelectionId;
import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TemporalType;

/** JPA persistence. Selection and entitlement revalidation share one transaction. */
public final class JpaCosmeticRepository implements CosmeticRepository {
    @Override
    public Snapshot load(UUID playerId, Date activeAt) {
        EntityManager em = HibernateUtil.createEntityManager();
        try {
            List<CosmeticEntitlement> active = em.createQuery(
                            "select e from CosmeticEntitlement e where e.playerId = :playerId "
                                    + "and e.revokedAt is null "
                                    + "and (e.expiresAt is null or e.expiresAt > :activeAt)",
                            CosmeticEntitlement.class)
                    .setParameter("playerId", playerId)
                    .setParameter("activeAt", activeAt)
                    .getResultList();
            Set<String> entitlements = new HashSet<>();
            Map<String, Date> expirations = new HashMap<>();
            Set<String> permanent = new HashSet<>();
            for (CosmeticEntitlement entitlement : active) {
                String cosmeticId = entitlement.getCosmeticId();
                entitlements.add(cosmeticId);
                Date expiresAt = entitlement.getExpiresAt();
                if (expiresAt == null) {
                    permanent.add(cosmeticId);
                    expirations.remove(cosmeticId);
                } else if (!permanent.contains(cosmeticId)) {
                    expirations.merge(cosmeticId, expiresAt,
                            (left, right) -> left.after(right) ? left : right);
                }
            }
            List<CosmeticSelection> rows = em.createQuery(
                            "select s from CosmeticSelection s where s.playerId = :playerId",
                            CosmeticSelection.class)
                    .setParameter("playerId", playerId)
                    .getResultList();
            EnumMap<CosmeticSlot, String> selections = new EnumMap<>(CosmeticSlot.class);
            rows.forEach(row -> selections.put(row.getSlot(), row.getCosmeticId()));
            return new Snapshot(entitlements, expirations, selections);
        } finally {
            em.close();
        }
    }

    @Override
    public PersistenceResult selectIfEntitled(
            UUID playerId, CosmeticSlot slot, String cosmeticId, Date selectedAt) {
        EntityManager em = HibernateUtil.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            List<CosmeticEntitlement> active = em.createQuery(
                            "select e from CosmeticEntitlement e "
                                    + "where e.playerId = :playerId "
                                    + "and e.cosmeticId = :cosmeticId "
                                    + "and e.revokedAt is null "
                                    + "and (e.expiresAt is null or e.expiresAt > :selectedAt)",
                            CosmeticEntitlement.class)
                    .setParameter("playerId", playerId)
                    .setParameter("cosmeticId", cosmeticId)
                    .setParameter("selectedAt", selectedAt)
                    .setLockMode(LockModeType.PESSIMISTIC_READ)
                    .getResultList();
            if (active.isEmpty()) {
                tx.rollback();
                return PersistenceResult.NOT_ENTITLED;
            }
            // PostgreSQL upsert avoids a first-selection race between Java, Bedrock
            // and a linked client while the entitlement row lock serializes revoke.
            em.createNativeQuery("""
                    insert into cosmetic_selections (player_id, slot, cosmetic_id, selected_at)
                    values (:playerId, :slot, :cosmeticId, :selectedAt)
                    on conflict (player_id, slot) do update set
                      cosmetic_id = excluded.cosmetic_id,
                      selected_at = excluded.selected_at
                    """)
                    .setParameter("playerId", playerId)
                    .setParameter("slot", slot.name())
                    .setParameter("cosmeticId", cosmeticId)
                    .setParameter("selectedAt", selectedAt)
                    .executeUpdate();
            tx.commit();
            return PersistenceResult.SELECTED;
        } catch (RuntimeException error) {
            rollback(tx);
            throw error;
        } finally {
            em.close();
        }
    }

    @Override
    public boolean deselect(UUID playerId, CosmeticSlot slot) {
        EntityManager em = HibernateUtil.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            CosmeticSelection selection = em.find(CosmeticSelection.class,
                    new CosmeticSelectionId(playerId, slot), LockModeType.PESSIMISTIC_WRITE);
            if (selection == null) {
                tx.commit();
                return false;
            }
            em.remove(selection);
            tx.commit();
            return true;
        } catch (RuntimeException error) {
            rollback(tx);
            throw error;
        } finally {
            em.close();
        }
    }

    @Override
    public void grantAll(
            UUID playerId,
            List<String> cosmeticIds,
            String source,
            Date grantedAt,
            Date expiresAt,
            Map<CosmeticSlot, String> selectIfEmpty) {
        EntityManager em = HibernateUtil.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            for (String cosmeticId : cosmeticIds) {
                // The stable source is part of the key. Replays extend the same
                // grant monotonically, while a revoked/refunded source can never
                // be resurrected by an older success event arriving afterwards.
                em.createNativeQuery("""
                        insert into cosmetic_entitlements
                          (player_id, cosmetic_id, source, granted_at, revoked_at, expires_at)
                        values (:playerId, :cosmeticId, :source, :grantedAt, null, :expiresAt)
                        on conflict (player_id, cosmetic_id, source) do update set
                          granted_at = least(cosmetic_entitlements.granted_at, excluded.granted_at),
                          expires_at = case
                            when cosmetic_entitlements.expires_at is null
                              or excluded.expires_at is null then null
                            else greatest(cosmetic_entitlements.expires_at, excluded.expires_at) end
                        """)
                        .setParameter("playerId", playerId)
                        .setParameter("cosmeticId", cosmeticId)
                        .setParameter("source", source)
                        .setParameter("grantedAt", grantedAt, TemporalType.TIMESTAMP)
                        .setParameter("expiresAt", expiresAt, TemporalType.TIMESTAMP)
                        .executeUpdate();
            }
            for (Map.Entry<CosmeticSlot, String> selection : selectIfEmpty.entrySet()) {
                em.createNativeQuery("""
                        insert into cosmetic_selections (player_id, slot, cosmetic_id, selected_at)
                        values (:playerId, :slot, :cosmeticId, :selectedAt)
                        on conflict (player_id, slot) do nothing
                        """)
                        .setParameter("playerId", playerId)
                        .setParameter("slot", selection.getKey().name())
                        .setParameter("cosmeticId", selection.getValue())
                        .setParameter("selectedAt", grantedAt, TemporalType.TIMESTAMP)
                        .executeUpdate();
            }
            tx.commit();
        } catch (RuntimeException error) {
            rollback(tx);
            throw error;
        } finally {
            em.close();
        }
    }

    @Override
    public int revokeTemporary(
            UUID playerId, List<String> cosmeticIds, String source, Date revokedAt) {
        EntityManager em = HibernateUtil.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            List<CosmeticEntitlement> temporary = em.createQuery(
                            "select e from CosmeticEntitlement e "
                                    + "where e.playerId = :playerId "
                                    + "and e.cosmeticId in :cosmeticIds "
                                    + "and e.source = :source "
                                    + "and e.expiresAt is not null "
                                    + "and e.revokedAt is null",
                            CosmeticEntitlement.class)
                    .setParameter("playerId", playerId)
                    .setParameter("cosmeticIds", cosmeticIds)
                    .setParameter("source", source)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultList();
            temporary.forEach(entitlement -> entitlement.revoke(revokedAt));
            tx.commit();
            return temporary.size();
        } catch (RuntimeException error) {
            rollback(tx);
            throw error;
        } finally {
            em.close();
        }
    }

    @Override
    public int revokeSource(UUID playerId, String source, Date revokedAt) {
        EntityManager em = HibernateUtil.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            List<CosmeticEntitlement> active = em.createQuery(
                            "select e from CosmeticEntitlement e "
                                    + "where e.playerId = :playerId "
                                    + "and e.source = :source "
                                    + "and e.revokedAt is null",
                            CosmeticEntitlement.class)
                    .setParameter("playerId", playerId)
                    .setParameter("source", source)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultList();
            active.forEach(entitlement -> entitlement.revoke(revokedAt));
            tx.commit();
            return active.size();
        } catch (RuntimeException error) {
            rollback(tx);
            throw error;
        } finally {
            em.close();
        }
    }

    @Override
    public boolean revoke(UUID playerId, String cosmeticId, Date revokedAt) {
        EntityManager em = HibernateUtil.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            List<CosmeticEntitlement> active = em.createQuery(
                            "select e from CosmeticEntitlement e "
                                    + "where e.playerId = :playerId "
                                    + "and e.cosmeticId = :cosmeticId "
                                    + "and e.revokedAt is null "
                                    + "and (e.expiresAt is null or e.expiresAt > :revokedAt)",
                            CosmeticEntitlement.class)
                    .setParameter("playerId", playerId)
                    .setParameter("cosmeticId", cosmeticId)
                    .setParameter("revokedAt", revokedAt)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultList();
            if (active.isEmpty()) {
                tx.commit();
                return false;
            }
            active.forEach(entitlement -> entitlement.revoke(revokedAt));
            tx.commit();
            return true;
        } catch (RuntimeException error) {
            rollback(tx);
            throw error;
        } finally {
            em.close();
        }
    }

    private static void rollback(EntityTransaction tx) {
        if (tx.isActive()) tx.rollback();
    }
}
