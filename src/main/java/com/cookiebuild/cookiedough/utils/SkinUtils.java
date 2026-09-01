package com.cookiebuild.cookiedough.utils;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class SkinUtils {
    public static ItemStack getPlayerHead(UUID playerId) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            // This would need to be implemented with Mojang API or a skin service
            // For now we'll use the player's actual skin if online
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
            head.setItemMeta(meta);
        }

        return head;
    }
}