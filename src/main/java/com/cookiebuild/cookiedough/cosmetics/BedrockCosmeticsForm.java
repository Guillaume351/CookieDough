package com.cookiebuild.cookiedough.cosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.geysermc.cumulus.form.SimpleForm;

/** Native Bedrock presentation of the same actions used by the Java inventory. */
final class BedrockCosmeticsForm {
    private BedrockCosmeticsForm() { }

    static SimpleForm create(CosmeticService.Inventory cosmetics, Function<String, String> message,
            Consumer<CosmeticMenuAction> onAction) {
        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§l§6" + message.apply("cosmetics.title"))
                .content("§7" + message.apply("cosmetics.content"));
        List<String> actions = new ArrayList<>();
        for (CosmeticMenuView.Entry entry : CosmeticMenuView.entries(cosmetics)) {
            CosmeticService.InventoryItem item = entry.item();
            String state = item.entitled()
                ? (!item.cosmetic().selectionRequired() ? message.apply("cosmetics.active")
                        : item.selected() ? message.apply("cosmetics.selected")
                        : message.apply("cosmetics.available"))
                : message.apply("cosmetics.locked");
            button(builder, actions, "§f§l" + message.apply(item.cosmetic().nameKey())
                + "\n§7" + state, entry.action());
        }
        if (cosmetics.selections().containsKey(CosmeticSlot.EMOTE)) {
            button(builder, actions, "§d" + message.apply("cosmetics.preview.emote"), "preview_emote");
        }
        if (cosmetics.selections().containsKey(CosmeticSlot.VICTORY_EFFECT)) {
            button(builder, actions, "§6" + message.apply("cosmetics.preview.victory"), "preview_victory");
        }
        builder.validResultHandler(response -> {
            int index = response.getClickedButtonId();
            if (index >= 0 && index < actions.size()) {
                CosmeticMenuAction.parse(actions.get(index)).ifPresent(onAction);
            }
        });
        return builder.build();
    }

    private static void button(SimpleForm.Builder builder, List<String> actions, String label, String action) {
        builder.button(label);
        actions.add(action);
    }
}
