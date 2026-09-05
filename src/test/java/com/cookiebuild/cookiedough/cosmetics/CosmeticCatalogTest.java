package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class CosmeticCatalogTest {
    @Test
    void firstCollectionUsesTheImmutableProductIdsAndSlots() {
        assertEquals(Set.of(
                CosmeticCatalog.SUPPORTER_BADGE,
                CosmeticCatalog.COOKIE_CRUMB_TRAIL,
                CosmeticCatalog.COOKIE_CHEER,
                CosmeticCatalog.GOLDEN_COOKIE_BURST,
                CosmeticCatalog.SUPPORTER_PROFILE_FRAME,
                CosmeticCatalog.LOBBY_FLIGHT,
                CosmeticCatalog.SUPPORTER_JOIN_FLAIR,
                CosmeticCatalog.COOKIE_SPARKLE_TRAIL),
                CosmeticCatalog.items().stream().map(CosmeticDefinition::id).collect(Collectors.toSet()));
        assertEquals(8, CosmeticCatalog.items().size());
        assertEquals(7, CosmeticCatalog.items().stream().map(CosmeticDefinition::slot).distinct().count());
        assertTrue(CosmeticCatalog.find(CosmeticCatalog.SUPPORTER_BADGE)
                .filter(item -> item.slot() == CosmeticSlot.BADGE).isPresent());
        assertTrue(CosmeticCatalog.find(CosmeticCatalog.SUPPORTER_JOIN_FLAIR)
                .filter(item -> !item.selectionRequired()).isPresent());
    }
}
