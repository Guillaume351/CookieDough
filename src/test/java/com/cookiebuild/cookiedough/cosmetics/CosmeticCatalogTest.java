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
                CosmeticCatalog.APP_COMPANION_BADGE,
                CosmeticCatalog.CHOCOLATE_CHIP_TRAIL,
                CosmeticCatalog.CHERRY_PETAL_TRAIL,
                CosmeticCatalog.SOUL_FLAME_TRAIL,
                CosmeticCatalog.RAINBOW_TRAIL,
                CosmeticCatalog.LUCKY_CLOVER_TRAIL,
                CosmeticCatalog.COOKIE_RAIN_VICTORY,
                CosmeticCatalog.TOTEM_VICTORY,
                CosmeticCatalog.FIREWORK_VICTORY),
                CosmeticCatalog.items().stream().map(CosmeticDefinition::id).collect(Collectors.toSet()));
        assertEquals(21, CosmeticCatalog.items().size());
        assertEquals(7, CosmeticCatalog.items().stream().map(CosmeticDefinition::slot).distinct().count());
        assertTrue(CosmeticCatalog.find(CosmeticCatalog.SUPPORTER_BADGE)
                .filter(item -> item.slot() == CosmeticSlot.BADGE).isPresent());
        assertTrue(CosmeticCatalog.find(CosmeticCatalog.SUPPORTER_JOIN_FLAIR)
                .filter(item -> !item.selectionRequired()).isPresent());
    }

    @Test
    void starterSparkTrailKeepsItsPriceAndParticle() {
        CosmeticDefinition starter = CosmeticCatalog.find("starter_spark_trail").orElseThrow();
        assertEquals(CosmeticSlot.HUB_TRAIL, starter.slot());
        assertEquals(CosmeticDefinition.Acquisition.COINS, starter.acquisition());
        assertEquals(250, starter.coinPrice());
        assertTrue(CosmeticEffects.trailParticle(CosmeticCatalog.STARTER_SPARK_TRAIL) != null);
    }

    @Test
    void coinShopOffersEveryPricePointFromAFirstDayImpulseBuyToALongTermGoal() {
        java.util.List<CosmeticDefinition> shop = CosmeticCatalog.coinShop();
        assertEquals(java.util.List.of(150, 250, 400, 500, 600, 750, 900, 1_200, 1_500, 2_000),
                shop.stream().map(CosmeticDefinition::coinPrice).toList());
        assertEquals(CosmeticCatalog.CHOCOLATE_CHIP_TRAIL, shop.getFirst().id());
        assertEquals(CosmeticCatalog.RAINBOW_TRAIL, shop.getLast().id());
        assertEquals(3, shop.stream().filter(item -> item.slot() == CosmeticSlot.VICTORY_EFFECT).count());
        assertTrue(shop.stream().noneMatch(CosmeticDefinition::free));
        CosmeticDefinition lucky = CosmeticCatalog.find(CosmeticCatalog.LUCKY_CLOVER_TRAIL).orElseThrow();
        assertEquals(CosmeticDefinition.Acquisition.REWARD, lucky.acquisition());
        assertEquals("cosmetics.unlock.ten_matches", lucky.unlockHintKey());
    }
}
