package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CosmeticCacheFreshnessTest {
    @Test
    void stalePermanentRightsFailClosedWhileDatabaseIsUnavailable() {
        Instant read = Instant.parse("2026-09-05T00:00:00Z");
        CosmeticDefinition badge = CosmeticCatalog.find(CosmeticCatalog.SUPPORTER_BADGE).orElseThrow();
        var inventory = new CosmeticService.Inventory(
                List.of(new CosmeticService.InventoryItem(badge, true, true)),
                Map.of(CosmeticSlot.BADGE, badge.id()), Map.of(), Map.of());
        var cache = CosmeticEffects.CachedCosmetics.from(inventory, read);
        assertTrue(cache.selected(CosmeticSlot.BADGE, badge.id(), read.plusSeconds(14)));
        assertFalse(cache.selected(CosmeticSlot.BADGE, badge.id(), read.plusSeconds(15)));
        assertFalse(cache.entitled(badge.id(), read.plusSeconds(15)));
    }

    @Test
    void expiryIsExclusiveEvenBeforeCacheRefreshAndAtEffectExecution() {
        Instant read = Instant.parse("2026-09-05T00:00:00Z");
        Instant expiry = read.plusSeconds(3);
        var inventory = new CosmeticService.Inventory(List.of(),
                Map.of(CosmeticSlot.LOBBY_FLIGHT, CosmeticCatalog.LOBBY_FLIGHT),
                Map.of(CosmeticCatalog.LOBBY_FLIGHT, expiry),
                Map.of(CosmeticSlot.LOBBY_FLIGHT, expiry));
        var cache = CosmeticEffects.CachedCosmetics.from(inventory, read);
        assertTrue(cache.selected(CosmeticSlot.LOBBY_FLIGHT, CosmeticCatalog.LOBBY_FLIGHT, read));
        assertFalse(cache.selected(CosmeticSlot.LOBBY_FLIGHT, CosmeticCatalog.LOBBY_FLIGHT, expiry));
        assertFalse(CosmeticEffectAuthorization.isSelected(inventory, CosmeticSlot.LOBBY_FLIGHT,
                CosmeticCatalog.LOBBY_FLIGHT, expiry));
    }

    @Test
    void slowDatabaseReadsDoNotReceiveANewFreshnessWindowOnCompletion() {
        Instant read = Instant.parse("2026-09-05T00:00:00Z");
        var inventory = new CosmeticService.Inventory(List.of(),
                Map.of(CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE), Map.of(), Map.of());
        var cache = CosmeticEffects.CachedCosmetics.from(inventory, read);
        assertFalse(cache.fresh(read.plusSeconds(16)));
    }
}
