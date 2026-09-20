package com.cookiebuild.cookiedough.cosmetics;

import java.util.ArrayList;
import java.util.List;

/** Shared Java/Bedrock action model. Labels are deliberately not identities. */
final class CosmeticMenuView {
    record Entry(CosmeticService.InventoryItem item, String action) {
    }

    private CosmeticMenuView() {
    }

    static List<Entry> entries(CosmeticService.Inventory inventory) {
        List<Entry> result = new ArrayList<>();
        for (CosmeticService.InventoryItem item : inventory.items()) {
            String action;
            if (!item.entitled()) {
                action = CosmeticMenuAction.locked(item.cosmetic());
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
