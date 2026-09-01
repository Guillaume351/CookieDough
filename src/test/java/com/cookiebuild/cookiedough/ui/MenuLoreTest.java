package com.cookiebuild.cookiedough.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

class MenuLoreTest {
    @Test
    void secondaryHierarchyIsExplicitAndHighContrastAcrossItemMetaApis() {
        assertEquals(Component.text("↳ Details", NamedTextColor.WHITE), MenuLore.detail("Details"));
        assertEquals(ChatColor.WHITE + "↳ Details", MenuLore.legacyDetail("Details"));
    }
}
