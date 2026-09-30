package com.cookiebuild.cookiedough.ui;

import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.utils.LocaleManager;

/**
 * Selects edition-specific wording ("right-click" is meaningless on a phone or
 * a controller). A Bedrock variant lives next to the Java key with the
 * {@value #BEDROCK_SUFFIX} suffix in every message bundle.
 */
public final class PlatformText {
    public static final String BEDROCK_SUFFIX = ".bedrock";

    private PlatformText() { }

    public static String key(String baseKey, boolean bedrock) {
        return bedrock ? baseKey + BEDROCK_SUFFIX : baseKey;
    }

    /** Edition-specific wording, falling back to the shared key when no Bedrock variant exists. */
    public static String message(Player player, String baseKey, Object... arguments) {
        java.util.Locale locale = player == null ? null : player.locale();
        if (BedrockFormSupport.isBedrock(player)) {
            String variant = key(baseKey, true);
            String value = LocaleManager.getMessage(variant, locale, arguments);
            if (!variant.equals(value)) return value;
        }
        return LocaleManager.getMessage(baseKey, locale, arguments);
    }
}
