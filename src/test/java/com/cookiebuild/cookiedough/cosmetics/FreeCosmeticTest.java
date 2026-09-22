package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FreeCosmeticTest {
    @Test
    void everyoneOwnsOnlyTheFreeBaselineWithoutBuyingOrCreatingGrantRows() {
        var repository = new InMemoryCosmeticRepository();
        var service = new CosmeticService(repository);
        UUID player = UUID.randomUUID();
        var inventory = service.inventory(player);
        assertEquals(1, inventory.items().stream().filter(CosmeticService.InventoryItem::entitled).count());
        assertTrue(inventory.entitled(CosmeticCatalog.COOKIE_SPARKLE_TRAIL));
        assertEquals(CosmeticService.SelectionResult.SELECTED,
                service.select(player, CosmeticSlot.HUB_TRAIL, CosmeticCatalog.COOKIE_SPARKLE_TRAIL));
        assertTrue(repository.sources(player, CosmeticCatalog.COOKIE_SPARKLE_TRAIL).isEmpty());
        assertEquals(CosmeticCatalog.COOKIE_SPARKLE_TRAIL,
                new CosmeticService(repository).inventory(player).selections().get(CosmeticSlot.HUB_TRAIL));
        assertEquals(CosmeticService.SelectionResult.NOT_ENTITLED,
                service.select(player, CosmeticSlot.HUB_TRAIL, CosmeticCatalog.COOKIE_CRUMB_TRAIL));
        assertEquals(CosmeticService.SelectionResult.SLOT_MISMATCH,
                service.select(player, CosmeticSlot.BADGE, CosmeticCatalog.COOKIE_SPARKLE_TRAIL));
        assertEquals(CosmeticService.SelectionResult.DESELECTED, service.deselect(player, CosmeticSlot.HUB_TRAIL));
        assertTrue(service.inventory(player).selections().isEmpty());
    }

    @Test
    void expiredOrRevokedHistoricalGrantCannotRemoveUniversalFreeOwnership() {
        var repository = new InMemoryCosmeticRepository();
        var service = new CosmeticService(repository);
        UUID player = UUID.randomUUID();
        repository.grant(player, CosmeticCatalog.COOKIE_SPARKLE_TRAIL, "old:fixture", new Date(),
                Date.from(Instant.now().plusSeconds(30)));
        service.select(player, CosmeticSlot.HUB_TRAIL, CosmeticCatalog.COOKIE_SPARKLE_TRAIL);
        var inventory = service.inventory(player);
        assertFalse(inventory.entitlementExpirations().containsKey(CosmeticCatalog.COOKIE_SPARKLE_TRAIL));
        assertFalse(inventory.selectionExpirations().containsKey(CosmeticSlot.HUB_TRAIL));
        service.revokeEntitlementSource(player, "old:fixture");
        assertTrue(service.inventory(player).entitled(CosmeticCatalog.COOKIE_SPARKLE_TRAIL));
        assertEquals(CosmeticCatalog.COOKIE_SPARKLE_TRAIL, service.inventory(player).selections().get(CosmeticSlot.HUB_TRAIL));
        var cached = CosmeticEffects.CachedCosmetics.from(service.inventory(player), Instant.now());
        assertTrue(cached.selected(CosmeticSlot.HUB_TRAIL, CosmeticCatalog.COOKIE_SPARKLE_TRAIL, Instant.now()));
    }
}
