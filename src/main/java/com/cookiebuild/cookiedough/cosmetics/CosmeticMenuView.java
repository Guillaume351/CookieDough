package com.cookiebuild.cookiedough.cosmetics;

import java.util.ArrayList;
import java.util.List;

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
        return switch (item.acquisition()) {
            case COINS -> new UnlockHint("cosmetics.unlock.coins", new Object[] { item.coinPrice() });
            case WEB_SHOP -> new UnlockHint("cosmetics.unlock.shop", new Object[0]);
            case REWARD -> new UnlockHint(item.unlockHintKey(), new Object[0]);
            case FREE -> new UnlockHint("cosmetics.free", new Object[0]);
        };
    }

    static List<Entry> entries(CosmeticService.Inventory inventory) {
        List<Entry> result = new ArrayList<>();
        for (CosmeticService.InventoryItem item : inventory.items()) {
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
}
