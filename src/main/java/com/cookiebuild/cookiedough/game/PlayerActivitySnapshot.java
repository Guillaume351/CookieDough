package com.cookiebuild.cookiedough.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Bounded in-memory reconnect state for one live arena participant. */
public record PlayerActivitySnapshot(
        ItemStack[] contents,
        ItemStack[] armor,
        ItemStack offHand,
        Location location,
        GameMode gameMode,
        double health,
        int food,
        float saturation,
        int level,
        float experience) {

    public PlayerActivitySnapshot {
        contents = cloneItems(contents);
        armor = cloneItems(armor);
        offHand = offHand == null ? null : offHand.clone();
        location = location == null ? null : location.clone();
    }

    public static PlayerActivitySnapshot capture(Player player) {
        return new PlayerActivitySnapshot(
                player.getInventory().getContents(), player.getInventory().getArmorContents(),
                player.getInventory().getItemInOffHand(), player.getLocation(), player.getGameMode(),
                player.getHealth(), player.getFoodLevel(), player.getSaturation(),
                player.getLevel(), player.getExp());
    }

    public boolean restore(Player player, Location fallback) {
        if (!relocate(player, fallback)) return false;
        applyState(player);
        return true;
    }

    /** Loads and verifies the saved destination before any inventory/state mutation. */
    public boolean relocate(Player player, Location fallback) {
        Location destination = location != null && location.getWorld() != null ? location : fallback;
        if (destination == null || destination.getWorld() == null
                || !destination.getChunk().load() || !player.teleport(destination)) {
            return false;
        }
        return true;
    }

    /** Applies the bounded snapshot after the module has atomically swapped its roster entry. */
    public void applyState(Player player) {
        player.getInventory().clear();
        player.getInventory().setContents(cloneItems(contents));
        player.getInventory().setArmorContents(cloneItems(armor));
        player.getInventory().setItemInOffHand(offHand == null ? null : offHand.clone());
        player.setGameMode(gameMode == null ? GameMode.SURVIVAL : gameMode);
        double maximumHealth = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.max(0.5, Math.min(health, maximumHealth)));
        player.setFoodLevel(Math.max(0, Math.min(food, 20)));
        player.setSaturation(Math.max(0.0f, saturation));
        player.setLevel(Math.max(0, level));
        player.setExp(Math.max(0.0f, Math.min(experience, 1.0f)));
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[items.length];
        for (int index = 0; index < items.length; index++) {
            copy[index] = items[index] == null ? null : items[index].clone();
        }
        return copy;
    }
}
