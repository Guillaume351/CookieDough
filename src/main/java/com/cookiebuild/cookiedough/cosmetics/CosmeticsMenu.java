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
    private static final int[] JAVA_SLOTS = { 9, 10, 11, 12, 13, 14, 15 };

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
        UUID playerId = player.getUniqueId();
        UUID viewId = UUID.randomUUID();
        views.put(playerId, viewId);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CosmeticService.Inventory inventory = service.inventory(playerId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || !viewId.equals(views.get(playerId))) return;
                    effects.refresh(player);
                    if (!openBedrock(player, inventory, viewId)) {
                        player.openInventory(javaInventory(player, inventory, viewId));
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

    private Inventory javaInventory(Player player, CosmeticService.Inventory cosmetics, UUID viewId) {
        CosmeticsHolder holder = new CosmeticsHolder(viewId, Component.text(
                message(player, "cosmetics.title"), NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        List<CosmeticMenuView.Entry> entries = CosmeticMenuView.entries(cosmetics);
        for (int index = 0; index < entries.size(); index++) {
            CosmeticMenuView.Entry entry = entries.get(index);
            CosmeticService.InventoryItem item = entry.item();
            List<String> lore = new ArrayList<>();
            lore.add(message(player, item.cosmetic().descriptionKey()));
            lore.add(item.entitled()
                    ? (!item.cosmetic().selectionRequired() ? message(player, "cosmetics.active")
                            : item.selected() ? message(player, "cosmetics.selected")
                            : message(player, "cosmetics.available"))
                    : message(player, "cosmetics.locked"));
            if (item.cosmetic().slot() == CosmeticSlot.PROFILE_FRAME) {
                lore.add(message(player, "cosmetics.profile_frame.game_fallback"));
            }
            inventory.setItem(JAVA_SLOTS[index], item(item.cosmetic().icon(),
                    message(player, item.cosmetic().nameKey()), entry.action(), lore));
        }
        if (cosmetics.selections().containsKey(CosmeticSlot.EMOTE)) {
            inventory.setItem(21, item(Material.AMETHYST_SHARD,
                    message(player, "cosmetics.preview.emote"), "preview_emote",
                    List.of(message(player, "cosmetics.preview.hub_only"))));
        }
        if (cosmetics.selections().containsKey(CosmeticSlot.VICTORY_EFFECT)) {
            inventory.setItem(23, item(Material.GLOWSTONE_DUST,
                    message(player, "cosmetics.preview.victory"), "preview_victory",
                    List.of(message(player, "cosmetics.preview.no_damage"))));
        }
        return inventory;
    }

    private boolean openBedrock(Player player, CosmeticService.Inventory cosmetics, UUID viewId) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) return false;
        try {
            FloodgatePlayer floodgate = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgate == null) return false;
            return floodgate.sendForm(BedrockCosmeticsForm.create(cosmetics,
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
            case SELECT -> mutate(player, bedrock, () -> service.select(
                    player.getUniqueId(), action.slot(), action.cosmeticId()));
            case DESELECT -> mutate(player, bedrock, () -> service.deselect(
                    player.getUniqueId(), action.slot()));
            case PREVIEW_EMOTE -> effects.playCelebration(player);
            case PREVIEW_VICTORY -> effects.previewVictoryEffect(player);
            case LOCKED -> player.sendMessage(ChatColor.YELLOW + message(player, "cosmetics.locked"));
            case NOOP -> { }
        }
    }

    private void mutate(Player player, boolean bedrock,
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
                    open(player);
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
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
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

        private CosmeticsHolder(UUID viewId, Component title) {
            this.viewId = viewId;
            inventory = Bukkit.createInventory(this, 27, title);
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
