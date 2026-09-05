package com.cookiebuild.cookiedough.cosmetics;

import java.time.Instant;

/** Shared final check used immediately before an effect is scheduled. */
final class CosmeticEffectAuthorization {
    private CosmeticEffectAuthorization() {
    }

    static boolean isSelected(
            CosmeticService.Inventory inventory, CosmeticSlot slot, String requiredCosmeticId) {
        return isSelected(inventory, slot, requiredCosmeticId, Instant.now());
    }

    static boolean isSelected(CosmeticService.Inventory inventory, CosmeticSlot slot,
            String requiredCosmeticId, Instant now) {
        if (inventory == null || !requiredCosmeticId.equals(inventory.selections().get(slot))) return false;
        Instant expiresAt = inventory.selectionExpirations().get(slot);
        return expiresAt == null || expiresAt.isAfter(now);
    }

    static boolean isEntitled(CosmeticService.Inventory inventory, String requiredCosmeticId) {
        return inventory != null && inventory.entitled(requiredCosmeticId)
                && (inventory.entitlementExpirations().get(requiredCosmeticId) == null
                    || inventory.entitlementExpirations().get(requiredCosmeticId).isAfter(Instant.now()));
    }
}
