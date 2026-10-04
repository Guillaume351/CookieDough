package com.cookiebuild.cookiedough.cosmetics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Shared Java/Bedrock action model. Labels are deliberately not identities. */
final class CosmeticMenuView {
    record Entry(CosmeticService.InventoryItem item, String action) {
    }

    private CosmeticMenuView() {
    }

    /** Plain-text key and arguments explaining how to unlock a cosmetic the player does not own. */
    record UnlockHint(String key, Object[] args) {
    }

    static UnlockHint unlockHint(CosmeticDefinition item) {
        return unlockHint(item, null);
    }

    /**
     * @param coins the player's balance when known; coin items then say whether
     *        they can be bought now or how many coins are still missing
     */
    static UnlockHint unlockHint(CosmeticDefinition item, Integer coins) {
        return switch (item.acquisition()) {
            case COINS -> coins == null
                    ? new UnlockHint("cosmetics.unlock.coins", new Object[] { item.coinPrice() })
                    : coins >= item.coinPrice()
                            ? new UnlockHint("cosmetics.unlock.coins_affordable", new Object[] { item.coinPrice() })
                            : new UnlockHint("cosmetics.unlock.coins_missing",
                                    new Object[] { item.coinPrice(), item.coinPrice() - coins });
            case WEB_SHOP -> new UnlockHint("cosmetics.unlock.shop", new Object[0]);
            case REWARD -> new UnlockHint(item.unlockHintKey(), new Object[0]);
            case FREE -> new UnlockHint("cosmetics.free", new Object[0]);
        };
    }

    /** The second line of an entry: what the player has or what tapping it does. */
    static String stateText(Function<String, String> message, CosmeticService.InventoryItem item, Integer coins) {
        CosmeticDefinition cosmetic = item.cosmetic();
        if (!item.entitled()) {
            UnlockHint hint = unlockHint(cosmetic, coins);
            return CosmeticsMenu.format(message, hint.key(), hint.args());
        }
        if (!cosmetic.selectionRequired()) return message.apply("cosmetics.active");
        if (item.selected()) return message.apply("cosmetics.selected");
        return message.apply(cosmetic.free() ? "cosmetics.free_equip" : "cosmetics.available");
    }

    static boolean affordable(CosmeticService.InventoryItem item, Integer coins) {
        return !item.entitled() && item.cosmetic().coinPurchasable() && coins != null
                && coins >= item.cosmetic().coinPrice();
    }

    static List<Entry> entries(CosmeticService.Inventory inventory) {
        return entries(inventory, null);
    }

    /**
     * Owned cosmetics first (equipped ones on top), then coin items by price,
     * then rewards, and premium web-shop items last, so the free trail and
     * anything the player can afford are visible without scrolling.
     */
    static List<Entry> entries(CosmeticService.Inventory inventory, Integer coins) {
        List<CosmeticService.InventoryItem> ordered = new ArrayList<>(inventory.items());
        List<CosmeticDefinition> catalog = CosmeticCatalog.items();
        ordered.sort(Comparator.<CosmeticService.InventoryItem>comparingInt(CosmeticMenuView::group)
                .thenComparing(item -> !(item.entitled() && item.selected() && item.cosmetic().selectionRequired()))
                .thenComparingInt(item -> item.entitled() ? 0 : item.cosmetic().coinPrice())
                .thenComparingInt(item -> catalog.indexOf(item.cosmetic())));
        List<Entry> result = new ArrayList<>();
        for (CosmeticService.InventoryItem item : ordered) {
            String action;
            if (!item.entitled()) {
                action = item.cosmetic().coinPurchasable()
                        ? CosmeticMenuAction.buy(item.cosmetic())
                        : CosmeticMenuAction.locked(item.cosmetic());
            } else if (!item.cosmetic().selectionRequired()) {
                action = "noop";
            } else if (item.selected()) {
                action = CosmeticMenuAction.deselect(item.cosmetic().slot());
            } else {
                action = CosmeticMenuAction.select(item.cosmetic());
            }
            result.add(new Entry(item, action));
        }
        return List.copyOf(result);
    }

    private static int group(CosmeticService.InventoryItem item) {
        if (item.entitled()) return 0;
        return switch (item.cosmetic().acquisition()) {
            case FREE -> 0;
            case COINS -> 1;
            case REWARD -> 2;
            case WEB_SHOP -> 3;
        };
    }
}
