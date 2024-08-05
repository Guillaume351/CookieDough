package com.cookiebuild.cookiedough.ui;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;

public class PopupManager {

    // Send a title and subtitle to a player
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(ChatColor.translateAlternateColorCodes('&', title),
                ChatColor.translateAlternateColorCodes('&', subtitle),
                fadeIn, stay, fadeOut);
    }

    // Send an action bar message to a player
    public void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message)));
    }
}