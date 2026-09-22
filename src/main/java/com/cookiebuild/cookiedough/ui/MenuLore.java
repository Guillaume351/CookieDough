package com.cookiebuild.cookiedough.ui;

import java.util.Objects;

import org.bukkit.ChatColor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Shared high-contrast secondary line for Java inventory controls. */
public final class MenuLore {
    private MenuLore() { }

    public static Component detail(String text) {
        return Component.text("↳ " + Objects.requireNonNull(text, "text"), NamedTextColor.WHITE);
    }

    public static String legacyDetail(String text) {
        return ChatColor.WHITE + "↳ " + Objects.requireNonNull(text, "text");
    }
}
