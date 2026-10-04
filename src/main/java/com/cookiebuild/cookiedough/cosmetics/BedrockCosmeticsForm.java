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
     * @param message resolves a key (arguments are substituted by the caller)
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
        for (CosmeticMenuView.Entry entry : CosmeticMenuView.entries(cosmetics, coins)) {
            CosmeticService.InventoryItem item = entry.item();
            boolean equipped = item.entitled() && item.selected() && item.cosmetic().selectionRequired();
            String nameColor = equipped ? "§2" : item.entitled() || CosmeticMenuView.affordable(item, coins)
                    ? "§0" : "§8";
            String label = nameColor + "§l" + message.apply(item.cosmetic().nameKey())
                    + "\n§r§8" + message.apply("cosmetics.slot." + item.cosmetic().slot().name())
                    + " • §r" + CosmeticMenuView.stateText(message, item, coins);
            button(builder, actions, label, entry.action(),
                    CosmeticMenuView.affordable(item, coins) ? "actions/purchase" : null);
        }
        if (cosmetics.selections().containsKey(CosmeticSlot.EMOTE)) {
            button(builder, actions, "§d" + message.apply("cosmetics.preview.emote"), "preview_emote",
                    "actions/preview");
        }
        String victory = cosmetics.selections().get(CosmeticSlot.VICTORY_EFFECT);
        if (victory != null) {
            String victoryName = CosmeticCatalog.find(victory).map(item -> message.apply(item.nameKey()))
                    .orElse(victory);
            button(builder, actions, "§6" + CosmeticsMenu.format(message, "cosmetics.preview.victory", victoryName),
                    "preview_victory", "actions/preview");
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

    private static void button(SimpleForm.Builder builder, List<String> actions, String label, String action,
            String image) {
        com.cookiebuild.cookiedough.ui.BedrockFormImages.button(builder, label, image);
        actions.add(action);
    }
}
