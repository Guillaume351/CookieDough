package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CosmeticCoinShopTest {
    private static final UUID PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private InMemoryCosmeticRepository repository;
    private CosmeticService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCosmeticRepository();
        service = new CosmeticService(repository,
                Clock.fixed(Instant.parse("2026-09-30T19:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void premiumWebShopCosmeticsAreNeverSoldForCoins() {
        repository.setCoins(PLAYER, 1_000_000);
        for (String premium : new String[] { CosmeticCatalog.COOKIE_CRUMB_TRAIL, CosmeticCatalog.SUPPORTER_BADGE,
                CosmeticCatalog.LOBBY_FLIGHT, CosmeticCatalog.GOLDEN_COOKIE_BURST }) {
            assertEquals(CosmeticService.PurchaseResult.NOT_FOR_SALE, service.purchaseWithCoins(PLAYER, premium));
        }
        assertEquals(CosmeticService.PurchaseResult.NOT_FOR_SALE,
                service.purchaseWithCoins(PLAYER, CosmeticCatalog.APP_COMPANION_BADGE));
        assertEquals(CosmeticService.PurchaseResult.NOT_FOR_SALE,
                service.purchaseWithCoins(PLAYER, CosmeticCatalog.STREAK_STAR_TRAIL));
        assertEquals(1_000_000, repository.coins(PLAYER));
    }

    @Test
    void purchaseDebitsTheCatalogPriceOnceAndEquips() {
        repository.setCoins(PLAYER, CosmeticCatalog.NOTE_TRAIL_PRICE + 5);
        assertEquals(CosmeticService.PurchaseResult.PURCHASED,
                service.purchaseWithCoins(PLAYER, CosmeticCatalog.NOTE_TRAIL));
        assertEquals(5, repository.coins(PLAYER));
        CosmeticService.Inventory inventory = service.inventory(PLAYER);
        assertTrue(inventory.entitled(CosmeticCatalog.NOTE_TRAIL));
        assertEquals(CosmeticCatalog.NOTE_TRAIL, inventory.selections().get(CosmeticSlot.HUB_TRAIL));

        repository.setCoins(PLAYER, 10_000);
        assertEquals(CosmeticService.PurchaseResult.ALREADY_OWNED,
                service.purchaseWithCoins(PLAYER, CosmeticCatalog.NOTE_TRAIL));
        assertEquals(10_000, repository.coins(PLAYER));
    }

    @Test
    void insufficientCoinsGrantNothing() {
        repository.setCoins(PLAYER, CosmeticCatalog.HEART_TRAIL_PRICE - 1);
        assertEquals(CosmeticService.PurchaseResult.INSUFFICIENT_COINS,
                service.purchaseWithCoins(PLAYER, CosmeticCatalog.HEART_TRAIL));
        assertFalse(service.inventory(PLAYER).entitled(CosmeticCatalog.HEART_TRAIL));
        assertEquals(CosmeticCatalog.HEART_TRAIL_PRICE - 1, repository.coins(PLAYER));
    }

    @Test
    void rewardGrantIsIdempotentAndOnlyEquipsAnEmptySlot() {
        service.grantReward(PLAYER, CosmeticCatalog.APP_COMPANION_BADGE, "reward:app_link:once");
        service.grantReward(PLAYER, CosmeticCatalog.APP_COMPANION_BADGE, "reward:app_link:once");
        assertEquals(CosmeticCatalog.APP_COMPANION_BADGE, service.inventory(PLAYER).selections().get(CosmeticSlot.BADGE));
        assertEquals(1, repository.sources(PLAYER, CosmeticCatalog.APP_COMPANION_BADGE).size());

        repository.injectSelection(PLAYER, CosmeticSlot.HUB_TRAIL, CosmeticCatalog.COOKIE_SPARKLE_TRAIL);
        service.grantReward(PLAYER, CosmeticCatalog.STREAK_STAR_TRAIL, "login-calendar:2026-09-30");
        assertEquals(CosmeticCatalog.COOKIE_SPARKLE_TRAIL,
                service.inventory(PLAYER).selections().get(CosmeticSlot.HUB_TRAIL));
        assertTrue(service.inventory(PLAYER).entitled(CosmeticCatalog.STREAK_STAR_TRAIL));
    }

    @Test
    void everyLockedCosmeticExplainsHowToGetIt() {
        for (CosmeticDefinition item : CosmeticCatalog.items()) {
            switch (item.acquisition()) {
                case FREE -> assertTrue(item.free());
                case COINS -> assertTrue(item.coinPrice() > 0);
                case WEB_SHOP -> assertEquals(0, item.coinPrice());
                case REWARD -> assertTrue(item.unlockHintKey() != null && !item.unlockHintKey().isBlank());
            }
        }
        assertEquals(10, CosmeticCatalog.coinShop().size());
    }

    @Test
    void definitionsRejectInconsistentPricing() {
        assertThrows(IllegalArgumentException.class, () -> new CosmeticDefinition("x", CosmeticSlot.HUB_TRAIL,
                Material.COOKIE, "n", "d", true, false, CosmeticDefinition.Acquisition.COINS, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new CosmeticDefinition("x", CosmeticSlot.HUB_TRAIL,
                Material.COOKIE, "n", "d", true, false, CosmeticDefinition.Acquisition.WEB_SHOP, 100, null));
        assertThrows(IllegalArgumentException.class, () -> new CosmeticDefinition("x", CosmeticSlot.HUB_TRAIL,
                Material.COOKIE, "n", "d", true, true, CosmeticDefinition.Acquisition.WEB_SHOP, 0, null));
    }
}
