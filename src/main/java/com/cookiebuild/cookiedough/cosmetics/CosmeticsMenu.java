package com.cookiebuild.cookiedough.cosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Cross-edition cosmetic inventory. Player actions never grant entitlements. */
public final class CosmeticsMenu implements Listener {
    /** Rows 2 to 5 of a double chest: room for 36 cosmetics. */
    static final int JAVA_FIRST_ITEM_SLOT = 9;
    static final int JAVA_ITEM_SLOTS = 36;
    static final int JAVA_SIZE = 54;

    private final CookieDough plugin;
    private final CosmeticService service;
    private final CosmeticEffects effects;
    private final NamespacedKey actionKey;
    private final Map<UUID, UUID> views = new HashMap<>();

    public CosmeticsMenu(CookieDough plugin, CosmeticService service, CosmeticEffects effects) {
        this.plugin = plugin;
        this.service = service;
        this.effects = effects;
        this.actionKey = new NamespacedKey(plugin, "cosmetics_action");
    }

    public void open(Player player) {
        open(player, "command");
    }

    /**
     * @param source where the player came from (command, hub, hotbar,
     *        postmatch, gift), recorded as server-side funnel telemetry
     */
    public void open(Player player, String source) {
        com.cookiebuild.cookiedough.game.FunnelTelemetry.record(player,
                com.cookiebuild.cookiedough.game.FunnelTelemetry.Event.COSMETICS_OPENED,
                "source=" + safeToken(source));
        reopen(player);
    }

    /** Re-renders after an action without counting a new visit. */
    private void reopen(Player player) {
        UUID playerId = player.getUniqueId();
        UUID viewId = UUID.randomUUID();
        views.put(playerId, viewId);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CosmeticService.Inventory inventory = service.inventory(playerId);
                Integer coins = readCoins(playerId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || !viewId.equals(views.get(playerId))) return;
                    effects.refresh(player);
                    if (!openBedrock(player, inventory, coins, viewId)) {
                        player.openInventory(javaInventory(player, inventory, coins, viewId));
                    }
                });
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Could not open cosmetics for " + player.getName()
                        + ": " + rootMessage(error));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.sendMessage(ChatColor.RED
                            + message(player, "cosmetics.error"));
                });
            }
        });
    }

    /** `/fly` uses the same entitlement and selection path as both native UIs. */
    public void toggleFlight(Player player) {
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CosmeticService.Inventory inventory = service.inventory(playerId);
                CosmeticService.SelectionResult result = inventory.entitled(CosmeticCatalog.LOBBY_FLIGHT)
                        ? (CosmeticCatalog.LOBBY_FLIGHT.equals(
                                inventory.selections().get(CosmeticSlot.LOBBY_FLIGHT))
                                ? service.deselect(playerId, CosmeticSlot.LOBBY_FLIGHT)
                                : service.select(playerId, CosmeticSlot.LOBBY_FLIGHT,
                                        CosmeticCatalog.LOBBY_FLIGHT))
                        : CosmeticService.SelectionResult.NOT_ENTITLED;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    effects.refresh(player);
                    player.sendMessage(resultMessage(player, result));
                });
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Could not toggle lobby flight for " + player.getName()
                        + ": " + rootMessage(error));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.sendMessage(ChatColor.RED + message(player, "cosmetics.error"));
                });
            }
        });
    }

    /** Public entry point for the hub/shop button: same as /cosmetics (/shop, /boutique). */
    public void openShop(Player player) {
        open(player, "hub");
    }

    public void openShop(Player player, String source) {
        open(player, source);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CosmeticsHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()
                || !holder.viewId.equals(views.get(player.getUniqueId()))) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String encoded = clicked.getItemMeta().getPersistentDataContainer()
                .get(actionKey, PersistentDataType.STRING);
        if ("back".equals(encoded)) {
            reopen(player);
            return;
        }
        CosmeticMenuAction.parse(encoded).ifPresent(action -> dispatch(player, action, false));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CosmeticsHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        views.remove(event.getPlayer().getUniqueId());
    }

    private Inventory javaInventory(Player player, CosmeticService.Inventory cosmetics, Integer coins, UUID viewId) {
        CosmeticsHolder holder = new CosmeticsHolder(viewId, JAVA_SIZE, Component.text(
                message(player, "cosmetics.title"), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        if (coins != null) {
            inventory.setItem(4, item(Material.GOLD_NUGGET, message(player, "cosmetics.balance", coins), "noop",
                    List.of(message(player, "cosmetics.balance_lore"))));
        }
        List<CosmeticMenuView.Entry> entries = CosmeticMenuView.entries(cosmetics, coins);
        for (int index = 0; index < entries.size() && index < JAVA_ITEM_SLOTS; index++) {
            CosmeticMenuView.Entry entry = entries.get(index);
            CosmeticService.InventoryItem item = entry.item();
            List<String> lore = new ArrayList<>();
            lore.add(message(player, item.cosmetic().descriptionKey()));
            lore.add(message(player, "cosmetics.slot." + item.cosmetic().slot().name()));
            lore.add(CosmeticMenuView.stateText(key -> message(player, key), item, coins));
            if (item.cosmetic().slot() == CosmeticSlot.PROFILE_FRAME) {
                lore.add(message(player, "cosmetics.profile_frame.game_fallback"));
            }
            NamedTextColor color = item.entitled() && item.selected() ? NamedTextColor.GREEN
                    : item.entitled() || CosmeticMenuView.affordable(item, coins) ? NamedTextColor.GOLD
                    : NamedTextColor.GRAY;
            ItemStack stack = item(item.cosmetic().icon(), message(player, item.cosmetic().nameKey()), entry.action(),
                    lore, color);
            if (item.entitled() && item.selected() && item.cosmetic().selectionRequired()) glint(stack);
            inventory.setItem(JAVA_FIRST_ITEM_SLOT + index, stack);
        }
        if (cosmetics.selections().containsKey(CosmeticSlot.EMOTE)) {
            inventory.setItem(48, item(Material.AMETHYST_SHARD,
                    message(player, "cosmetics.preview.emote"), "preview_emote",
                    List.of(message(player, "cosmetics.preview.hub_only"))));
        }
        inventory.setItem(49, item(Material.WRITABLE_BOOK, message(player, "cosmetics.web_shop.name"), "noop",
                List.of(message(player, "cosmetics.unlock.shop"), message(player, "cosmetics.web_shop.lore"))));
        String victory = cosmetics.selections().get(CosmeticSlot.VICTORY_EFFECT);
        if (victory != null) {
            inventory.setItem(50, item(Material.GLOWSTONE_DUST,
                    message(player, "cosmetics.preview.victory", victoryName(player, victory)), "preview_victory",
                    List.of(message(player, "cosmetics.preview.no_damage"))));
        }
        return inventory;
    }

    private static String victoryName(Player player, String cosmeticId) {
        return CosmeticCatalog.find(cosmeticId).map(item -> message(player, item.nameKey())).orElse(cosmeticId);
    }

    private static void glint(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
    }

    private Inventory javaConfirm(Player player, CosmeticDefinition cosmetic, UUID viewId) {
        CosmeticsHolder holder = new CosmeticsHolder(viewId, 27, Component.text(
                message(player, "cosmetics.buy.title"), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        String question = message(player, "cosmetics.buy.confirm", message(player, cosmetic.nameKey()),
                cosmetic.coinPrice());
        inventory.setItem(13, item(cosmetic.icon(), message(player, cosmetic.nameKey()), "noop",
                List.of(message(player, cosmetic.descriptionKey()), question)));
        inventory.setItem(11, item(Material.LIME_CONCRETE, message(player, "cosmetics.buy.yes"),
                CosmeticMenuAction.confirmBuy(cosmetic), List.of(question)));
        inventory.setItem(15, item(Material.RED_CONCRETE, message(player, "cosmetics.buy.no"), "back",
                List.of()));
        return inventory;
    }

    /** Plain-text unlock hint ("750 pièces", "Boutique : cookie-build.com/shop", ...). */
    static String unlockText(java.util.function.Function<String, String> message, CosmeticDefinition cosmetic) {
        CosmeticMenuView.UnlockHint hint = CosmeticMenuView.unlockHint(cosmetic);
        return format(message, hint.key(), hint.args());
    }

    static String safeToken(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^A-Za-z0-9_-]", "");
    }

    static String format(java.util.function.Function<String, String> message, String key, Object... args) {
        String text = message.apply(key);
        for (int index = 0; index < args.length; index++) {
            text = text.replace("{" + index + "}", String.valueOf(args[index]));
        }
        return text;
    }

    private Integer readCoins(UUID playerId) {
        try {
            return CookieDough.createMinigameProgressionService().getCoins(playerId, null);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private boolean openBedrock(Player player, CosmeticService.Inventory cosmetics, Integer coins, UUID viewId) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) return false;
        try {
            FloodgatePlayer floodgate = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgate == null) return false;
            return floodgate.sendForm(BedrockCosmeticsForm.create(cosmetics, coins,
                    key -> message(player, key), action -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && viewId.equals(views.get(player.getUniqueId()))) {
                            dispatch(player, action, true);
                        }
                    })));
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Could not open Bedrock cosmetics for " + player.getName()
                    + ": " + rootMessage(error));
            return false;
        }
    }

    private void dispatch(Player player, CosmeticMenuAction action, boolean bedrock) {
        switch (action.kind()) {
            case SELECT -> mutate(player, bedrock, action, () -> service.select(
                    player.getUniqueId(), action.slot(), action.cosmeticId()));
            case DESELECT -> mutate(player, bedrock, action, () -> service.deselect(
                    player.getUniqueId(), action.slot()));
            case PREVIEW_EMOTE -> effects.playCelebration(player);
            case PREVIEW_VICTORY -> effects.previewVictoryEffect(player);
            case LOCKED -> CosmeticCatalog.find(action.cosmeticId()).ifPresent(cosmetic -> player.sendMessage(
                    ChatColor.YELLOW + message(player, cosmetic.nameKey()) + ChatColor.GRAY + " — "
                            + unlockText(key -> message(player, key), cosmetic)));
            case BUY -> CosmeticCatalog.find(action.cosmeticId()).ifPresent(cosmetic -> confirm(player, cosmetic,
                    bedrock));
            case CONFIRM_BUY -> purchase(player, action.cosmeticId(), bedrock);
            case NOOP -> { }
        }
    }

    private void confirm(Player player, CosmeticDefinition cosmetic, boolean bedrock) {
        UUID viewId = UUID.randomUUID();
        views.put(player.getUniqueId(), viewId);
        if (bedrock) {
            try {
                FloodgatePlayer floodgate = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
                if (floodgate != null && floodgate.sendForm(BedrockCosmeticsForm.confirmPurchase(cosmetic,
                        key -> message(player, key),
                        () -> Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline() && viewId.equals(views.get(player.getUniqueId()))) {
                                purchase(player, cosmetic.id(), true);
                            }
                        }),
                        () -> Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline() && viewId.equals(views.get(player.getUniqueId()))) reopen(player);
                        })))) {
                    return;
                }
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().warning("Could not open Bedrock purchase confirmation: " + rootMessage(error));
            }
        }
        player.openInventory(javaConfirm(player, cosmetic, viewId));
    }

    private void purchase(Player player, String cosmeticId, boolean bedrock) {
        views.remove(player.getUniqueId());
        if (!bedrock) player.closeInventory();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CosmeticService.PurchaseResult result = service.purchaseWithCoins(player.getUniqueId(), cosmeticId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    effects.refresh(player);
                    String name = CosmeticCatalog.find(cosmeticId).map(item -> message(player, item.nameKey()))
                            .orElse(cosmeticId);
                    boolean success = result == CosmeticService.PurchaseResult.PURCHASED;
                    player.sendMessage(Component.text(message(player, switch (result) {
                        case PURCHASED -> "cosmetics.buy.success";
                        case ALREADY_OWNED -> "cosmetics.buy.already_owned";
                        case INSUFFICIENT_COINS -> "cosmetics.buy.insufficient";
                        default -> "cosmetics.buy.unavailable";
                    }, name), success ? NamedTextColor.GREEN : NamedTextColor.RED));
                    if (success) {
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
                        CosmeticCatalog.find(cosmeticId).ifPresent(item -> com.cookiebuild.cookiedough.game
                                .FunnelTelemetry.record(player, com.cookiebuild.cookiedough.game.FunnelTelemetry
                                        .Event.COSMETIC_PURCHASED, "cosmetic=" + item.id() + " slot="
                                        + item.slot().name() + " price=" + item.coinPrice()));
                    }
                    reopen(player);
                });
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Could not buy cosmetic " + cosmeticId + " for " + player.getName()
                        + ": " + rootMessage(error));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.sendMessage(ChatColor.RED + message(player, "cosmetics.error"));
                });
            }
        });
    }

    private void mutate(Player player, boolean bedrock, CosmeticMenuAction action,
            java.util.function.Supplier<CosmeticService.SelectionResult> operation) {
        views.remove(player.getUniqueId());
        if (!bedrock) player.closeInventory();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CosmeticService.SelectionResult result = operation.get();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    effects.refresh(player);
                    player.sendMessage(resultMessage(player, result));
                    if (result == CosmeticService.SelectionResult.SELECTED) {
                        com.cookiebuild.cookiedough.game.FunnelTelemetry.record(player,
                                com.cookiebuild.cookiedough.game.FunnelTelemetry.Event.COSMETIC_SELECTED,
                                "cosmetic=" + action.cosmeticId() + " slot=" + action.slot().name());
                    }
                    reopen(player);
                });
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Could not update cosmetics for " + player.getName()
                        + ": " + rootMessage(error));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.sendMessage(ChatColor.RED + message(player, "cosmetics.error"));
                });
            }
        });
    }

    private Component resultMessage(Player player, CosmeticService.SelectionResult result) {
        boolean success = result == CosmeticService.SelectionResult.SELECTED
                || result == CosmeticService.SelectionResult.DESELECTED
                || result == CosmeticService.SelectionResult.ALREADY_DESELECTED;
        String key = switch (result) {
            case SELECTED -> "cosmetics.result.selected";
            case DESELECTED, ALREADY_DESELECTED -> "cosmetics.result.deselected";
            case NOT_ENTITLED -> "cosmetics.result.not_entitled";
            case UNKNOWN_COSMETIC, SLOT_MISMATCH -> "cosmetics.result.invalid";
        };
        return Component.text(message(player, key), success ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    private ItemStack item(Material material, String name, String action, List<String> lore) {
        return item(material, name, action, lore, NamedTextColor.GOLD);
    }

    private ItemStack item(Material material, String name, String action, List<String> lore, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private static String message(Player player, String key, Object... args) {
        return LocaleManager.getMessage(key, player.locale(), args);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static final class CosmeticsHolder implements InventoryHolder {
        private final Inventory inventory;
        private final UUID viewId;

        private CosmeticsHolder(UUID viewId, int size, Component title) {
            this.viewId = viewId;
            inventory = Bukkit.createInventory(this, size, title);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private Inventory inventory() {
            return inventory;
        }
    }
}
