package com.cookiebuild.cookiedough.cosmetics;

import java.util.Set;
import java.util.regex.Pattern;

import org.bukkit.Material;

/**
 * Immutable, server-owned description of one cosmetic.
 *
 * @param acquisition how a player who does not own it can get it; shown as
 *        plain text in both menus (Bedrock cannot open chat links)
 * @param coinPrice price in coins when {@code acquisition == COINS}, else 0
 * @param unlockHintKey message key describing a reward-only unlock, or null
 * @param season id of the seasonal theme (e.g. {@code halloween}) this
 *        limited cosmetic belongs to, or null for a permanent one. A seasonal
 *        cosmetic is sold and listed only while its season is active; players
 *        who own it keep, see and equip it forever.
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
        String unlockHintKey,
        String season) {
    /** Same shape as {@code seasonal_collectibles.theme_id varchar(32)} and the theme folder names. */
    private static final Pattern SEASON_ID = Pattern.compile("[a-z0-9_]{1,32}");

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
        if (season != null && (!SEASON_ID.matcher(season).matches() || free)) {
            throw new IllegalArgumentException("A season is a lowercase theme id on a non-free cosmetic: " + id);
        }
    }

    public CosmeticDefinition(String id, CosmeticSlot slot, Material icon, String nameKey, String descriptionKey,
            boolean selectionRequired, boolean free, Acquisition acquisition, int coinPrice, String unlockHintKey) {
        this(id, slot, icon, nameKey, descriptionKey, selectionRequired, free, acquisition, coinPrice,
                unlockHintKey, null);
    }

    public CosmeticDefinition(String id, CosmeticSlot slot, Material icon, String nameKey, String descriptionKey,
            boolean selectionRequired, boolean free) {
        this(id, slot, icon, nameKey, descriptionKey, selectionRequired, free, null, 0, null, null);
    }

    /** Sold for coins at some point (always, or only during its season). */
    public boolean coinPurchasable() {
        return acquisition == Acquisition.COINS && coinPrice > 0;
    }

    /** True for a limited cosmetic tied to a seasonal theme. */
    public boolean seasonal() {
        return season != null;
    }

    /**
     * True for a permanent cosmetic, or for a seasonal one whose season is in
     * {@code activeSeasons} (lowercase theme ids).
     */
    public boolean inSeason(Set<String> activeSeasons) {
        return season == null || (activeSeasons != null && activeSeasons.contains(season));
    }

    /** Can be bought for coins right now, given the active seasons. */
    public boolean onSale(Set<String> activeSeasons) {
        return coinPurchasable() && inSeason(activeSeasons);
    }

    /** Message key of the "limited edition" label, or null for a permanent cosmetic. */
    public String seasonLabelKey() {
        return season == null ? null : "cosmetics.season." + season + ".label";
    }
}
