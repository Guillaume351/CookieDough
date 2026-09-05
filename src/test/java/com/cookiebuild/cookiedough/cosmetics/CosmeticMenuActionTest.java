package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class CosmeticMenuActionTest {
    @Test
    void actionIdentityUsesOpaqueCatalogueIdsAndSlotsNotVisibleTitles() {
        CosmeticDefinition badge = CosmeticCatalog.find(CosmeticCatalog.SUPPORTER_BADGE).orElseThrow();
        CosmeticMenuAction parsed = CosmeticMenuAction.parse(CosmeticMenuAction.select(badge)).orElseThrow();
        assertEquals(CosmeticMenuAction.Kind.SELECT, parsed.kind());
        assertEquals(CosmeticSlot.BADGE, parsed.slot());
        assertEquals(CosmeticCatalog.SUPPORTER_BADGE, parsed.cosmeticId());
        assertFalse(CosmeticMenuAction.parse("Supporter badge").isPresent());
        assertFalse(CosmeticMenuAction.parse("select:EMOTE:supporter_badge").isPresent());
    }

    @Test
    void javaAndBedrockViewEntriesShareTheSameValidatedActions() {
        InMemoryCosmeticRepository repository = new InMemoryCosmeticRepository();
        UUID player = UUID.randomUUID();
        repository.grant(player, CosmeticCatalog.COOKIE_CHEER, "test", new java.util.Date(), null);
        repository.grant(player, CosmeticCatalog.SUPPORTER_JOIN_FLAIR,
                "subscription:test", new java.util.Date(),
                new java.util.Date(System.currentTimeMillis() + 60_000L));
        CosmeticService service = new CosmeticService(repository);
        service.select(player, CosmeticSlot.EMOTE, CosmeticCatalog.COOKIE_CHEER);

        var entries = CosmeticMenuView.entries(service.inventory(player));
        assertEquals(7, entries.size());
        assertTrue(entries.stream().map(CosmeticMenuView.Entry::action)
                .allMatch(action -> CosmeticMenuAction.parse(action).isPresent()));
        assertTrue(entries.stream().anyMatch(entry -> entry.action().equals("deselect:EMOTE")));
        assertTrue(entries.stream().anyMatch(entry -> entry.item().cosmetic().id()
                .equals(CosmeticCatalog.SUPPORTER_JOIN_FLAIR)
                && entry.item().selected()
                && entry.action().equals("noop")));
    }
}
