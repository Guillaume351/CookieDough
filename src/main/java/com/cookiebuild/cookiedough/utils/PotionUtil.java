package com.cookiebuild.cookiedough.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PotionUtil {
    public static ItemStack createPotion(PotionEffectType type, int duration, int amplifier) {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.addCustomEffect(new PotionEffect(type, duration, amplifier), true);
        potion.setItemMeta(meta);
        return potion;
    }

    public static ItemStack createSplashPotion(PotionEffectType type, int duration, int amplifier) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.addCustomEffect(new PotionEffect(type, duration, amplifier), true);
        potion.setItemMeta(meta);
        return potion;
    }

    public static ItemStack createLingeringPotion(PotionEffectType type, int duration, int amplifier) {
        ItemStack potion = new ItemStack(Material.LINGERING_POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.addCustomEffect(new PotionEffect(type, duration, amplifier), true);
        potion.setItemMeta(meta);
        return potion;
    }
}