package com.cookiebuild.cookiedough.cosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;

/** Native Bedrock presentation of the same actions used by the Java inventory. */
final class BedrockCosmeticsForm {
    private BedrockCosmeticsForm() { }

    static SimpleForm create(CosmeticService.Inventory cosmetics, Function<String, String> message,
            Consumer<CosmeticMenuAction> onAction) {
        return create(cosmetics, null, message, onAction);
    }

    /**
     * @param coins the player's balance, or null when it could not be read
     * @param message resolves a key with optional arguments ({@code key|arg})
     */
    static SimpleForm create(CosmeticService.Inventory cosmetics, Integer coins, Function<String, String> message,
            Consumer<CosmeticMenuAction> onAction) {
        String content = "§7" + message.apply("cosmetics.content");
        if (coins != null) {
            content += "\n§6" + CosmeticsMenu.format(message, "cosmetics.balance", coins);
        }
        content += "\n§b" + message.apply("cosmetics.unlock.shop");
        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§l§6" + message.apply("cosmetics.title"))
                .content(content);
        List<String> actions = new ArrayList<>();
        for (CosmeticMenuView.Entry entry : CosmeticMenuView.entries(cosmetics)) {
            CosmeticService.InventoryItem item = entry.item();
            String state = item.entitled()
                ? (!item.cosmetic().selectionRequired() ? message.apply("cosmetics.active")
                        : item.selected() ? message.apply("cosmetics.selected")
                        : message.apply(item.cosmetic().free() ? "cosmetics.free" : "cosmetics.available"))
                : CosmeticsMenu.unlockText(message, item.cosmetic());
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

    /** Yes/no confirmation before spending coins; closing the form cancels. */
    static ModalForm confirmPurchase(CosmeticDefinition item, Function<String, String> message,
            Runnable onConfirm, Runnable onCancel) {
        return ModalForm.builder()
                .title("§l§6" + message.apply("cosmetics.buy.title"))
                .content(CosmeticsMenu.format(message, "cosmetics.buy.confirm",
                        message.apply(item.nameKey()), item.coinPrice()))
                .button1("§a" + message.apply("cosmetics.buy.yes"))
                .button2("§c" + message.apply("cosmetics.buy.no"))
                .validResultHandler(response -> {
                    if (response.clickedFirst()) onConfirm.run();
                    else onCancel.run();
                })
                .closedOrInvalidResultHandler(onCancel)
                .build();
    }

    private static void button(SimpleForm.Builder builder, List<String> actions, String label, String action) {
        builder.button(label);
        actions.add(action);
    }
}
