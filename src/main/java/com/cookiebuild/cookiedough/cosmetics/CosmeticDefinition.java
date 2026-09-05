package com.cookiebuild.cookiedough.cosmetics;

import org.bukkit.Material;

/** Immutable, server-owned description of one cosmetic. */
public record CosmeticDefinition(
        String id,
        CosmeticSlot slot,
        Material icon,
        String nameKey,
        String descriptionKey,
        boolean selectionRequired,
        boolean free) {
}
