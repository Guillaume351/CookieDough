package com.cookiebuild.cookiedough.game;

import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

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
        float experience,
        List<PotionEffect> potionEffects,
        double absorption,
        int fireTicks,
        int freezeTicks,
        float exhaustion,
        Component displayName,
        Component playerListName,
        boolean allowFlight,
        boolean flying) {

    public PlayerActivitySnapshot {
        contents = cloneItems(contents);
        armor = cloneItems(armor);
        offHand = offHand == null ? null : offHand.clone();
        location = location == null ? null : location.clone();
        potionEffects = potionEffects == null ? List.of() : List.copyOf(potionEffects);
    }

    public static PlayerActivitySnapshot capture(Player player) {
        return new PlayerActivitySnapshot(
                player.getInventory().getContents(), player.getInventory().getArmorContents(),
                player.getInventory().getItemInOffHand(), player.getLocation(), player.getGameMode(),
                player.getHealth(), player.getFoodLevel(), player.getSaturation(),
                player.getLevel(), player.getExp(), List.copyOf(player.getActivePotionEffects()),
                player.getAbsorptionAmount(), player.getFireTicks(), player.getFreezeTicks(),
                player.getExhaustion(), player.displayName(), player.playerListName(),
                player.getAllowFlight(), player.isFlying());
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
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        potionEffects.forEach(effect -> player.addPotionEffect(effect, true));
        player.setAbsorptionAmount(Math.max(0.0, absorption));
        player.setFireTicks(Math.max(0, fireTicks));
        player.setFreezeTicks(Math.max(0, freezeTicks));
        player.setExhaustion(Math.max(0.0f, exhaustion));
        player.displayName(displayName);
        player.playerListName(playerListName);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
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
