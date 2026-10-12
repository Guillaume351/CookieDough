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
                CosmeticCatalog.FIREWORK_VICTORY,
                CosmeticCatalog.PUMPKIN_TRAIL,
                CosmeticCatalog.WITCH_SPELL_VICTORY,
                CosmeticCatalog.WILL_O_WISP_TRAIL),
                CosmeticCatalog.items().stream().map(CosmeticDefinition::id).collect(Collectors.toSet()));
        assertEquals(24, CosmeticCatalog.items().size());
        // The Java chest lists every cosmetic in rows 2 to 5 of a double chest.
        assertTrue(CosmeticCatalog.items().size() <= CosmeticsMenu.JAVA_ITEM_SLOTS);
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
        java.util.List<CosmeticDefinition> shop = CosmeticCatalog.coinShop(Set.of());
        assertEquals(java.util.List.of(150, 250, 400, 500, 600, 750, 900, 1_200, 1_500, 2_000),
                shop.stream().map(CosmeticDefinition::coinPrice).toList());
        assertEquals(CosmeticCatalog.CHOCOLATE_CHIP_TRAIL, shop.getFirst().id());
        assertEquals(CosmeticCatalog.RAINBOW_TRAIL, shop.getLast().id());
        assertEquals(3, shop.stream().filter(item -> item.slot() == CosmeticSlot.VICTORY_EFFECT).count());
        assertTrue(shop.stream().noneMatch(CosmeticDefinition::free));
        CosmeticDefinition lucky = CosmeticCatalog.find(CosmeticCatalog.LUCKY_CLOVER_TRAIL).orElseThrow();
        assertEquals(CosmeticDefinition.Acquisition.REWARD, lucky.acquisition());
        assertEquals("cosmetics.unlock.ten_matches", lucky.unlockHintKey());
        assertTrue(shop.stream().noneMatch(CosmeticDefinition::seasonal));
    }

    @Test
    void halloweenLimitedEditionUsesTheContractIdsPricesAndSeason() {
        assertEquals(com.cookiebuild.cookiedough.theme.ThemeService.HALLOWEEN, CosmeticCatalog.HALLOWEEN_SEASON);
        assertEquals("halloween", CosmeticCatalog.HALLOWEEN_SEASON);
        assertEquals(Set.of("halloween"), CosmeticCatalog.seasons());

        CosmeticDefinition pumpkin = CosmeticCatalog.find("pumpkin_trail").orElseThrow();
        assertEquals(CosmeticSlot.HUB_TRAIL, pumpkin.slot());
        assertEquals(CosmeticDefinition.Acquisition.COINS, pumpkin.acquisition());
        assertEquals(450, pumpkin.coinPrice());
        assertEquals("halloween", pumpkin.season());
        assertEquals("cosmetics.season.halloween.label", pumpkin.seasonLabelKey());

        CosmeticDefinition witch = CosmeticCatalog.find("witch_spell_victory").orElseThrow();
        assertEquals(CosmeticSlot.VICTORY_EFFECT, witch.slot());
        assertEquals(CosmeticDefinition.Acquisition.COINS, witch.acquisition());
        assertEquals(900, witch.coinPrice());
        assertEquals("halloween", witch.season());

        CosmeticDefinition wisp = CosmeticCatalog.find("will_o_wisp_trail").orElseThrow();
        assertEquals(CosmeticSlot.HUB_TRAIL, wisp.slot());
        assertEquals(CosmeticDefinition.Acquisition.REWARD, wisp.acquisition());
        assertEquals(0, wisp.coinPrice());
        assertEquals(null, wisp.season());
        assertEquals("cosmetics.unlock.pumpkin_hunt", wisp.unlockHintKey());

        // Every coin item ever sold, and what is on sale with and without Halloween.
        assertEquals(java.util.List.of(150, 250, 400, 450, 500, 600, 750, 900, 900, 1_200, 1_500, 2_000),
                CosmeticCatalog.coinShop().stream().map(CosmeticDefinition::coinPrice).toList());
        assertEquals(CosmeticCatalog.coinShop(), CosmeticCatalog.coinShop(Set.of("halloween")));
        assertEquals(10, CosmeticCatalog.coinShop(Set.of("christmas")).size());
        assertTrue(CosmeticCatalog.coinShop(Set.of()).stream().noneMatch(item -> item.id().equals("pumpkin_trail")));
        assertTrue(CosmeticCatalog.coinShop().stream().noneMatch(item -> item.id().equals("will_o_wisp_trail")));
    }

    @Test
    void seasonsAreLowercaseThemeIdsOnNonFreeCosmetics() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new CosmeticDefinition(
                "x", CosmeticSlot.HUB_TRAIL, org.bukkit.Material.COOKIE, "n", "d", true, false,
                CosmeticDefinition.Acquisition.COINS, 10, null, "Halloween"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new CosmeticDefinition(
                "x", CosmeticSlot.HUB_TRAIL, org.bukkit.Material.COOKIE, "n", "d", true, true,
                CosmeticDefinition.Acquisition.FREE, 0, null, "halloween"));
        CosmeticDefinition christmas = new CosmeticDefinition("x", CosmeticSlot.HUB_TRAIL,
                org.bukkit.Material.COOKIE, "n", "d", true, false, CosmeticDefinition.Acquisition.COINS, 10, null,
                "christmas");
        assertTrue(christmas.onSale(Set.of("christmas", "halloween")));
        assertTrue(!christmas.onSale(Set.of("halloween")) && !christmas.onSale(null));
        assertTrue(christmas.inSeason(Set.of("christmas")));
    }
}
