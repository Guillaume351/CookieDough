package com.cookiebuild.cookiedough.cosmetics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Adventure-only name decoration; the original display name remains intact. */
public final class SupporterTitleFormatter {
    private SupporterTitleFormatter() {
    }

    public static Component decorate(Component displayName, boolean supporter) {
        if (!supporter) return displayName;
        return Component.text("[Supporter] ", NamedTextColor.GOLD).append(displayName);
    }
}
