package com.cookiebuild.cookiedough.cosmetics;

import org.bukkit.Material;

/**
 * Immutable, server-owned description of one cosmetic.
 *
 * @param acquisition how a player who does not own it can get it; shown as
 *        plain text in both menus (Bedrock cannot open chat links)
 * @param coinPrice price in coins when {@code acquisition == COINS}, else 0
 * @param unlockHintKey message key describing a reward-only unlock, or null
 */
public record CosmeticDefinition(
        String id,
        CosmeticSlot slot,
        Material icon,
        String nameKey,
        String descriptionKey,
        boolean selectionRequired,
        boolean free,
        Acquisition acquisition,
        int coinPrice,
        String unlockHintKey) {
    public enum Acquisition {
        /** Everyone owns it. */
        FREE,
        /** Paid on the web shop (never sold for coins). */
        WEB_SHOP,
        /** Bought in game with coins. */
        COINS,
        /** Earned only (app link, login streak, ...). */
        REWARD
    }

    public CosmeticDefinition {
        if (acquisition == null) acquisition = free ? Acquisition.FREE : Acquisition.WEB_SHOP;
        if (free != (acquisition == Acquisition.FREE)) {
            throw new IllegalArgumentException("Free cosmetics must use the FREE acquisition: " + id);
        }
        if ((acquisition == Acquisition.COINS) != (coinPrice > 0) || coinPrice < 0) {
            throw new IllegalArgumentException("Only coin cosmetics have a positive coin price: " + id);
        }
    }

    public CosmeticDefinition(String id, CosmeticSlot slot, Material icon, String nameKey, String descriptionKey,
            boolean selectionRequired, boolean free) {
        this(id, slot, icon, nameKey, descriptionKey, selectionRequired, free, null, 0, null);
    }

    public boolean coinPurchasable() {
        return acquisition == Acquisition.COINS && coinPrice > 0;
    }
}
