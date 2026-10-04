package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.utils.LocaleManager;

class CosmeticMenuViewTest {
    private static final Function<String, String> FRENCH = key -> LocaleManager.getMessage(key, Locale.FRENCH);

    @Test
    void aNewPlayerSeesTheFreeTrailFirstThenCoinItemsByPriceAndPremiumItemsLast() {
        CosmeticService service = new CosmeticService(new InMemoryCosmeticRepository());
        List<CosmeticMenuView.Entry> entries = CosmeticMenuView.entries(service.inventory(UUID.randomUUID()), 0);
        assertEquals(CosmeticCatalog.COOKIE_SPARKLE_TRAIL, entries.getFirst().item().cosmetic().id());
        assertEquals("select:HUB_TRAIL:cookie_sparkle_trail", entries.getFirst().action());
        List<Integer> coinPrices = entries.stream().map(entry -> entry.item().cosmetic())
                .filter(CosmeticDefinition::coinPurchasable).map(CosmeticDefinition::coinPrice).toList();
        assertEquals(coinPrices.stream().sorted().toList(), coinPrices);
        assertEquals(CosmeticCatalog.CHOCOLATE_CHIP_TRAIL, entries.get(1).item().cosmetic().id());
        int lastCoin = lastIndex(entries, CosmeticDefinition.Acquisition.COINS);
        int firstWeb = firstIndex(entries, CosmeticDefinition.Acquisition.WEB_SHOP);
        int firstReward = firstIndex(entries, CosmeticDefinition.Acquisition.REWARD);
        assertTrue(lastCoin < firstReward && firstReward < firstWeb);
        assertEquals(CosmeticDefinition.Acquisition.WEB_SHOP, entries.getLast().item().cosmetic().acquisition());
    }

    @Test
    void equippedCosmeticsComeFirstAndOwnedOnesBeforeAnythingToBuy() {
        InMemoryCosmeticRepository repository = new InMemoryCosmeticRepository();
        CosmeticService service = new CosmeticService(repository);
        UUID player = UUID.randomUUID();
        repository.grant(player, CosmeticCatalog.HEART_TRAIL, "coins:test", new Date(), null);
        service.select(player, CosmeticSlot.HUB_TRAIL, CosmeticCatalog.HEART_TRAIL);
        List<CosmeticMenuView.Entry> entries = CosmeticMenuView.entries(service.inventory(player), 10_000);
        assertEquals(CosmeticCatalog.HEART_TRAIL, entries.get(0).item().cosmetic().id());
        assertEquals("deselect:HUB_TRAIL", entries.get(0).action());
        assertEquals(CosmeticCatalog.COOKIE_SPARKLE_TRAIL, entries.get(1).item().cosmetic().id());
        assertTrue(entries.get(2).action().startsWith("buy:"));
    }

    @Test
    void coinItemsSayWhetherTheyCanBeBoughtNowOrHowManyCoinsAreMissing() {
        CosmeticDefinition chocolate = CosmeticCatalog.find(CosmeticCatalog.CHOCOLATE_CHIP_TRAIL).orElseThrow();
        CosmeticService.InventoryItem locked = new CosmeticService.InventoryItem(chocolate, false, false);
        assertEquals("150 pièces — tu peux l’acheter !", CosmeticMenuView.stateText(FRENCH, locked, 200));
        assertEquals("150 pièces — encore 110 à gagner", CosmeticMenuView.stateText(FRENCH, locked, 40));
        assertTrue(CosmeticMenuView.stateText(FRENCH, locked, null).contains("150"));
        assertTrue(CosmeticMenuView.affordable(locked, 150));
        assertFalse(CosmeticMenuView.affordable(locked, 149));
        assertFalse(CosmeticMenuView.affordable(locked, null));

        CosmeticDefinition free = CosmeticCatalog.find(CosmeticCatalog.COOKIE_SPARKLE_TRAIL).orElseThrow();
        assertEquals("Gratuit — touche pour équiper", CosmeticMenuView.stateText(FRENCH,
                new CosmeticService.InventoryItem(free, true, false), 0));
        assertEquals(LocaleManager.getMessage("cosmetics.selected", Locale.FRENCH), CosmeticMenuView.stateText(FRENCH,
                new CosmeticService.InventoryItem(free, true, true), 0));
        CosmeticDefinition lucky = CosmeticCatalog.find(CosmeticCatalog.LUCKY_CLOVER_TRAIL).orElseThrow();
        assertEquals("Récompense : joue 10 parties", CosmeticMenuView.stateText(FRENCH,
                new CosmeticService.InventoryItem(lucky, false, false), 9_999));
        assertFalse(CosmeticMenuView.affordable(new CosmeticService.InventoryItem(lucky, false, false), 9_999));
    }

    @Test
    void everyCosmeticAndSlotIsTranslatedInEveryLocaleWithBedrockSafeNames() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN, Locale.ITALIAN, Locale.of("es"),
                Locale.of("pt", "BR"), Locale.of("bg"), Locale.of("hi"))) {
            for (CosmeticDefinition item : CosmeticCatalog.items()) {
                String name = LocaleManager.getMessage(item.nameKey(), locale);
                assertFalse(name.equals(item.nameKey()), item.id() + " " + locale);
                assertFalse(LocaleManager.getMessage(item.descriptionKey(), locale).equals(item.descriptionKey()));
                assertTrue(name.codePoints().allMatch(Character::isBmpCodePoint), "emoji in " + name);
                if (item.unlockHintKey() != null) {
                    assertFalse(LocaleManager.getMessage(item.unlockHintKey(), locale).equals(item.unlockHintKey()));
                }
            }
            for (CosmeticSlot slot : CosmeticSlot.values()) {
                String key = "cosmetics.slot." + slot.name();
                assertFalse(LocaleManager.getMessage(key, locale).equals(key), key + " " + locale);
            }
        }
    }

    private static int firstIndex(List<CosmeticMenuView.Entry> entries, CosmeticDefinition.Acquisition acquisition) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).item().cosmetic().acquisition() == acquisition) return index;
        }
        return -1;
    }

    private static int lastIndex(List<CosmeticMenuView.Entry> entries, CosmeticDefinition.Acquisition acquisition) {
        int last = -1;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).item().cosmetic().acquisition() == acquisition) last = index;
        }
        return last;
    }
}
