package com.cookiebuild.cookiedough.ui;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Builds readable Cumulus button copy without relying on Minecraft colour codes.
 * The two explicit glyphs preserve label/detail hierarchy when Bedrock renders a
 * neutral button skin or when the optional resource pack is unavailable.
 */
public final class BedrockButtonText {
    private static final Pattern LEGACY_COLOUR = Pattern.compile("(?i)\u00a7[0-9A-FK-ORX]");

    private BedrockButtonText() { }

    public static String format(String label) {
        return format(label, "");
    }

    public static String format(String label, String detail) {
        String primary = plain(Objects.requireNonNull(label, "label")).trim();
        if (primary.isBlank()) throw new IllegalArgumentException("label must not be blank");
        String secondary = plain(detail == null ? "" : detail).trim();
        return secondary.isBlank() ? "\u25b6 " + primary : "\u25b6 " + primary + "\n\u21b3 " + secondary;
    }

    static String plain(String value) {
        return LEGACY_COLOUR.matcher(value).replaceAll("");
    }
}
