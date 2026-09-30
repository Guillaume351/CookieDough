package com.cookiebuild.cookiedough.cosmetics;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface CosmeticRepository {
    record Snapshot(
            Set<String> activeEntitlements,
            Map<String, Date> entitlementExpirations,
            Map<CosmeticSlot, String> selections) {
        public Snapshot {
            activeEntitlements = Set.copyOf(activeEntitlements);
            entitlementExpirations = Map.copyOf(entitlementExpirations);
            selections = Map.copyOf(selections);
        }
    }

    enum PersistenceResult {
        SELECTED,
        NOT_ENTITLED
    }

    enum PurchaseResult {
        PURCHASED,
        ALREADY_OWNED,
        INSUFFICIENT_COINS,
        PLAYER_NOT_FOUND
    }

    Snapshot load(UUID playerId, Date activeAt);

    PersistenceResult selectIfEntitled(UUID playerId, CosmeticSlot slot, String cosmeticId, Date selectedAt);

    boolean deselect(UUID playerId, CosmeticSlot slot);

    void grantAll(
            UUID playerId,
            List<String> cosmeticIds,
            String source,
            Date grantedAt,
            Date expiresAt,
            Map<CosmeticSlot, String> selectIfEmpty);

    default void grant(UUID playerId, String cosmeticId, String source, Date grantedAt, Date expiresAt) {
        grantAll(playerId, List.of(cosmeticId), source, grantedAt, expiresAt, Map.of());
    }

    int revokeTemporary(
            UUID playerId, List<String> cosmeticIds, String source, Date revokedAt);

    int revokeSource(UUID playerId, String source, Date revokedAt);

    boolean revoke(UUID playerId, String cosmeticId, Date revokedAt);

    /**
     * Atomically debits {@code price} coins (ledger row {@code cosmetic:<id>}),
     * grants a permanent entitlement and equips it in {@code slot}. Never
     * charges a player who already owns the cosmetic.
     */
    PurchaseResult purchaseWithCoins(UUID playerId, String cosmeticId, CosmeticSlot slot, int price,
            String source, Date purchasedAt);
}
