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
                CosmeticCatalog.COOKIE_SPARKLE_TRAIL,
                CosmeticCatalog.STARTER_SPARK_TRAIL,
                CosmeticCatalog.NOTE_TRAIL,
                CosmeticCatalog.HEART_TRAIL,
                CosmeticCatalog.STREAK_STAR_TRAIL,
                CosmeticCatalog.APP_COMPANION_BADGE),
                CosmeticCatalog.items().stream().map(CosmeticDefinition::id).collect(Collectors.toSet()));
        assertEquals(13, CosmeticCatalog.items().size());
        assertEquals(7, CosmeticCatalog.items().stream().map(CosmeticDefinition::slot).distinct().count());
        assertTrue(CosmeticCatalog.find(CosmeticCatalog.SUPPORTER_BADGE)
                .filter(item -> item.slot() == CosmeticSlot.BADGE).isPresent());
        assertTrue(CosmeticCatalog.find(CosmeticCatalog.SUPPORTER_JOIN_FLAIR)
                .filter(item -> !item.selectionRequired()).isPresent());
    }

    @Test
    void starterSparkTrailIsTheCheapestCoinCosmeticAndHasAParticle() {
        CosmeticDefinition starter = CosmeticCatalog.find("starter_spark_trail").orElseThrow();
        assertEquals(CosmeticSlot.HUB_TRAIL, starter.slot());
        assertEquals(CosmeticDefinition.Acquisition.COINS, starter.acquisition());
        assertEquals(250, starter.coinPrice());
        assertEquals(CosmeticCatalog.STARTER_SPARK_TRAIL, CosmeticCatalog.coinShop().getFirst().id());
        assertTrue(CosmeticCatalog.coinShop().stream().allMatch(item -> item.coinPrice() >= starter.coinPrice()));
        assertTrue(CosmeticEffects.trailParticle(CosmeticCatalog.STARTER_SPARK_TRAIL) != null);
    }
}
