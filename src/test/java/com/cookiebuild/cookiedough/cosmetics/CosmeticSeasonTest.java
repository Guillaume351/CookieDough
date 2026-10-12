package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.utils.LocaleManager;

/** Halloween limited edition: on sale only in season, kept and usable forever by owners. */
class CosmeticSeasonTest {
    private static final UUID PLAYER = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final Function<String, String> FRENCH = key -> LocaleManager.getMessage(key, Locale.FRENCH);
    private static final List<Locale> LOCALES = List.of(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN,
            Locale.ITALIAN, Locale.of("es"), Locale.of("pt", "BR"), Locale.of("bg"), Locale.of("hi"));

    private InMemoryCosmeticRepository repository;
    private CosmeticService service;
    private final AtomicReference<Set<String>> seasons = new AtomicReference<>(Set.of());

    @BeforeEach
    void setUp() {
        repository = new InMemoryCosmeticRepository();
        service = new CosmeticService(repository, Clock.fixed(Instant.parse("2026-10-20T18:00:00Z"), ZoneOffset.UTC));
        service.setActiveSeasons(seasons::get);
    }

    @Test
    void withoutAThemeEngineNoSeasonIsActive() {
        CosmeticService unwired = new CosmeticService(new InMemoryCosmeticRepository());
        assertEquals(Set.of(), unwired.activeSeasons());
        assertFalse(unwired.onSale(CosmeticCatalog.find(CosmeticCatalog.PUMPKIN_TRAIL).orElseThrow()));
        assertTrue(unwired.onSale(CosmeticCatalog.find(CosmeticCatalog.NOTE_TRAIL).orElseThrow()));
    }

    @Test
    void inSeasonTheLimitedItemsAreListedLabelledAndBuyable() {
        seasons.set(Set.of("halloween"));
        CosmeticService.Inventory inventory = service.inventory(PLAYER);
        assertEquals(Set.of("halloween"), inventory.activeSeasons());

        List<CosmeticMenuView.Entry> entries = CosmeticMenuView.entries(inventory, 0);
        CosmeticMenuView.Entry pumpkin = entry(entries, CosmeticCatalog.PUMPKIN_TRAIL).orElseThrow();
        CosmeticMenuView.Entry witch = entry(entries, CosmeticCatalog.WITCH_SPELL_VICTORY).orElseThrow();
        assertEquals("buy:pumpkin_trail", pumpkin.action());
        assertEquals("buy:witch_spell_victory", witch.action());
        assertEquals("Édition limitée Halloween", CosmeticMenuView.seasonLabel(FRENCH, pumpkin.item()));
        assertEquals("Édition limitée Halloween", CosmeticMenuView.seasonLabel(FRENCH, witch.item()));
        assertEquals("450 pièces — encore 450 à gagner", CosmeticMenuView.stateText(FRENCH, pumpkin.item(), 0));
        assertTrue(CosmeticMenuView.affordable(pumpkin.item(), 450));
        // Permanent items and the reward trail carry no limited-edition label.
        assertNull(CosmeticMenuView.seasonLabel(FRENCH,
                entry(entries, CosmeticCatalog.NOTE_TRAIL).orElseThrow().item()));
        assertNull(CosmeticMenuView.seasonLabel(FRENCH,
                entry(entries, CosmeticCatalog.WILL_O_WISP_TRAIL).orElseThrow().item()));
        // Coin items stay sorted by price, the Halloween ones included.
        List<Integer> prices = entries.stream().map(entry -> entry.item().cosmetic())
                .filter(CosmeticDefinition::coinPurchasable).map(CosmeticDefinition::coinPrice).toList();
        assertEquals(prices.stream().sorted().toList(), prices);
        assertEquals(12, prices.size());

        repository.setCoins(PLAYER, CosmeticCatalog.PUMPKIN_TRAIL_PRICE + 10);
        assertEquals(CosmeticService.PurchaseResult.PURCHASED,
                service.purchaseWithCoins(PLAYER, CosmeticCatalog.PUMPKIN_TRAIL));
        assertEquals(10, repository.coins(PLAYER));
        assertEquals(CosmeticCatalog.PUMPKIN_TRAIL, service.inventory(PLAYER).selections().get(CosmeticSlot.HUB_TRAIL));
    }

    @Test
    void outOfSeasonThePurchaseIsRefusedServerSideAndNothingIsDebited() {
        repository.setCoins(PLAYER, 10_000);
        for (String id : List.of(CosmeticCatalog.PUMPKIN_TRAIL, CosmeticCatalog.WITCH_SPELL_VICTORY)) {
            assertEquals(CosmeticService.PurchaseResult.OUT_OF_SEASON, service.purchaseWithCoins(PLAYER, id));
        }
        seasons.set(Set.of("christmas"));
        assertEquals(CosmeticService.PurchaseResult.OUT_OF_SEASON,
                service.purchaseWithCoins(PLAYER, CosmeticCatalog.PUMPKIN_TRAIL));
        assertEquals(10_000, repository.coins(PLAYER));
        assertFalse(service.inventory(PLAYER).entitled(CosmeticCatalog.PUMPKIN_TRAIL));
        assertFalse(service.inventory(PLAYER).entitled(CosmeticCatalog.WITCH_SPELL_VICTORY));
        // The reward trail is never sold, whatever the season.
        seasons.set(Set.of("halloween"));
        assertEquals(CosmeticService.PurchaseResult.NOT_FOR_SALE,
                service.purchaseWithCoins(PLAYER, CosmeticCatalog.WILL_O_WISP_TRAIL));
        assertEquals(10_000, repository.coins(PLAYER));
    }

    @Test
    void aMenuOpenedJustBeforeTheSeasonEndsCannotBuyAfterIt() {
        seasons.set(Set.of("halloween"));
        CosmeticService.Inventory openedInSeason = service.inventory(PLAYER);
        assertTrue(item(openedInSeason, CosmeticCatalog.PUMPKIN_TRAIL).onSale());
        seasons.set(Set.of());
        repository.setCoins(PLAYER, 10_000);
        assertEquals(CosmeticService.PurchaseResult.OUT_OF_SEASON,
                service.purchaseWithCoins(PLAYER, CosmeticCatalog.PUMPKIN_TRAIL));
        assertEquals(CosmeticMenuAction.Kind.LOCKED,
                CosmeticMenuAction.parse("buy:pumpkin_trail", service.activeSeasons()).orElseThrow().kind());
        assertEquals(10_000, repository.coins(PLAYER));
    }

    @Test
    void outOfSeasonUnownedLimitedItemsAreHiddenFromEveryMenu() {
        CosmeticService.Inventory inventory = service.inventory(PLAYER);
        List<CosmeticMenuView.Entry> entries = CosmeticMenuView.entries(inventory, 1_000_000);
        assertTrue(entry(entries, CosmeticCatalog.PUMPKIN_TRAIL).isEmpty());
        assertTrue(entry(entries, CosmeticCatalog.WITCH_SPELL_VICTORY).isEmpty());
        assertEquals(CosmeticCatalog.items().size() - 2, entries.size());
        assertTrue(entries.stream().noneMatch(entry -> entry.action().contains("pumpkin_trail")
                || entry.action().contains("witch_spell_victory")));
        CosmeticService.InventoryItem pumpkin = item(inventory, CosmeticCatalog.PUMPKIN_TRAIL);
        assertFalse(pumpkin.visible());
        assertFalse(pumpkin.onSale());
        assertFalse(CosmeticMenuView.affordable(pumpkin, 1_000_000));
        assertEquals("Plus en vente : l’édition limitée est terminée",
                CosmeticMenuView.stateText(FRENCH, pumpkin, 1_000_000));

        // The pumpkin-hunt reward is not seasonal: it stays listed with its unlock hint.
        CosmeticMenuView.Entry wisp = entry(entries, CosmeticCatalog.WILL_O_WISP_TRAIL).orElseThrow();
        assertEquals("locked:will_o_wisp_trail", wisp.action());
        assertEquals("Récompense : trouve les 13 citrouilles cachées du lobby pendant Halloween",
                CosmeticMenuView.stateText(FRENCH, wisp.item(), 1_000_000));
    }

    @Test
    void ownersKeepSeeingSelectingAndUsingTheirLimitedItemsOutOfSeason() {
        repository.grant(PLAYER, CosmeticCatalog.PUMPKIN_TRAIL, "coins:pumpkin_trail:1", new Date(), null);
        repository.grant(PLAYER, CosmeticCatalog.WITCH_SPELL_VICTORY, "coins:witch_spell_victory:1", new Date(), null);

        List<CosmeticMenuView.Entry> entries = CosmeticMenuView.entries(service.inventory(PLAYER), 0);
        CosmeticMenuView.Entry pumpkin = entry(entries, CosmeticCatalog.PUMPKIN_TRAIL).orElseThrow();
        assertEquals("select:HUB_TRAIL:pumpkin_trail", pumpkin.action());
        assertNull(CosmeticMenuView.seasonLabel(FRENCH, pumpkin.item()));
        assertEquals("select:VICTORY_EFFECT:witch_spell_victory",
                entry(entries, CosmeticCatalog.WITCH_SPELL_VICTORY).orElseThrow().action());

        assertEquals(CosmeticService.SelectionResult.SELECTED,
                service.select(PLAYER, CosmeticSlot.HUB_TRAIL, CosmeticCatalog.PUMPKIN_TRAIL));
        assertEquals(CosmeticService.SelectionResult.SELECTED,
                service.select(PLAYER, CosmeticSlot.VICTORY_EFFECT, CosmeticCatalog.WITCH_SPELL_VICTORY));
        CosmeticService.Inventory inventory = service.inventory(PLAYER);
        assertEquals("deselect:HUB_TRAIL", entry(CosmeticMenuView.entries(inventory, 0),
                CosmeticCatalog.PUMPKIN_TRAIL).orElseThrow().action());
        assertTrue(CosmeticEffectAuthorization.isSelected(inventory, CosmeticSlot.VICTORY_EFFECT,
                CosmeticCatalog.WITCH_SPELL_VICTORY));
        assertFalse(CosmeticParticles.trail(inventory.selections().get(CosmeticSlot.HUB_TRAIL), 0).isEmpty());
        assertFalse(CosmeticParticles.victory(inventory.selections().get(CosmeticSlot.VICTORY_EFFECT)).isEmpty());
        // Already owned: the shop never offers them again.
        assertTrue(CosmeticActivation.bestAffordable(inventory, 1_000_000)
                .filter(CosmeticDefinition::seasonal).isEmpty());
    }

    @Test
    void thePostMatchOfferSkipsOutOfSeasonItemsAndPrefersTheLimitedOneOnATie() {
        // Out of season: 450 coins buy Cherry Petals (400), 900 coins buy Victory Confetti (900).
        assertEquals(CosmeticCatalog.CHERRY_PETAL_TRAIL,
                CosmeticActivation.bestAffordable(service.inventory(PLAYER), 499).orElseThrow().id());
        assertEquals(CosmeticCatalog.TOTEM_VICTORY,
                CosmeticActivation.bestAffordable(service.inventory(PLAYER), 900).orElseThrow().id());
        seasons.set(Set.of("halloween"));
        assertEquals(CosmeticCatalog.PUMPKIN_TRAIL,
                CosmeticActivation.bestAffordable(service.inventory(PLAYER), 499).orElseThrow().id());
        assertEquals(CosmeticCatalog.WITCH_SPELL_VICTORY,
                CosmeticActivation.bestAffordable(service.inventory(PLAYER), 900).orElseThrow().id());
        assertEquals(CosmeticCatalog.RAINBOW_TRAIL,
                CosmeticActivation.bestAffordable(service.inventory(PLAYER), 1_000_000).orElseThrow().id());
    }

    @Test
    void thePumpkinHuntRewardIsGrantedIdempotentlyAndEquippedOnlyIntoAnEmptySlot() {
        assertEquals(CosmeticService.GrantResult.GRANTED,
                service.grantReward(PLAYER, CosmeticCatalog.WILL_O_WISP_TRAIL, "halloween-2026:pumpkins"));
        assertEquals(CosmeticService.GrantResult.GRANTED,
                service.grantReward(PLAYER, CosmeticCatalog.WILL_O_WISP_TRAIL, "halloween-2026:pumpkins"));
        assertEquals(1, repository.sources(PLAYER, CosmeticCatalog.WILL_O_WISP_TRAIL).size());
        CosmeticService.Inventory inventory = service.inventory(PLAYER);
        assertTrue(inventory.entitled(CosmeticCatalog.WILL_O_WISP_TRAIL));
        assertEquals(CosmeticCatalog.WILL_O_WISP_TRAIL, inventory.selections().get(CosmeticSlot.HUB_TRAIL));
        // Still selectable once Halloween is over.
        assertEquals("deselect:HUB_TRAIL", entry(CosmeticMenuView.entries(inventory, 0),
                CosmeticCatalog.WILL_O_WISP_TRAIL).orElseThrow().action());
    }

    @Test
    void theSeasonSupplierIsNormalisedAndFailsClosed() {
        seasons.set(Set.of(" Halloween "));
        assertEquals(Set.of("halloween"), service.activeSeasons());
        assertTrue(service.onSale(CosmeticCatalog.find(CosmeticCatalog.PUMPKIN_TRAIL).orElseThrow()));
        seasons.set(null);
        assertEquals(Set.of(), service.activeSeasons());
        service.setActiveSeasons(() -> {
            throw new IllegalStateException("theme engine not ready");
        });
        assertEquals(Set.of(), service.activeSeasons());
        repository.setCoins(PLAYER, 10_000);
        assertEquals(CosmeticService.PurchaseResult.OUT_OF_SEASON,
                service.purchaseWithCoins(PLAYER, CosmeticCatalog.PUMPKIN_TRAIL));
        assertFalse(item(service.inventory(PLAYER), CosmeticCatalog.PUMPKIN_TRAIL).visible());
    }

    @Test
    void halloweenTextsAreTranslatedEverywhereWithTheFrenchNamesTheWebsiteMirrors() {
        assertEquals("Traînée Citrouille", FRENCH.apply("cosmetics.item.pumpkin_trail.name"));
        assertEquals("Victoire Sortilège", FRENCH.apply("cosmetics.item.witch_spell_victory.name"));
        assertEquals("Feux follets", FRENCH.apply("cosmetics.item.will_o_wisp_trail.name"));
        for (Locale locale : LOCALES) {
            for (String season : CosmeticCatalog.seasons()) {
                String key = "cosmetics.season." + season + ".label";
                assertFalse(LocaleManager.getMessage(key, locale).equals(key), key + " " + locale);
            }
            for (String key : List.of("cosmetics.unlock.out_of_season", "cosmetics.buy.out_of_season",
                    "cosmetics.unlock.pumpkin_hunt")) {
                String text = LocaleManager.getMessage(key, locale, "X");
                assertFalse(text.equals(key), key + " " + locale);
                assertTrue(text.codePoints().allMatch(Character::isBmpCodePoint), key + " " + locale);
            }
            assertTrue(LocaleManager.getMessage("cosmetics.buy.out_of_season", locale, "Pumpkin").contains("Pumpkin"));
        }
    }

    private static CosmeticService.InventoryItem item(CosmeticService.Inventory inventory, String id) {
        return inventory.items().stream().filter(item -> item.cosmetic().id().equals(id)).findFirst().orElseThrow();
    }

    private static Optional<CosmeticMenuView.Entry> entry(List<CosmeticMenuView.Entry> entries, String id) {
        return entries.stream().filter(entry -> entry.item().cosmetic().id().equals(id)).findFirst();
    }
}
