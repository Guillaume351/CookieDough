package com.cookiebuild.cookiedough.theme;

import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/**
 * Which theme is active: {@code auto} follows each theme's window,
 * {@code off} disables seasonal themes, any other value forces that theme.
 */
public final class ThemeSchedule {
    public static final String AUTO = "auto";
    public static final String OFF = "off";

    private ThemeSchedule() {
    }

    public static Optional<ThemeDefinition> resolve(String mode, Collection<ThemeDefinition> themes, Instant now) {
        String normalized = mode == null ? AUTO : mode.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) normalized = AUTO;
        if (normalized.equals(OFF)) return Optional.empty();
        if (normalized.equals(AUTO)) {
            return themes.stream().filter(theme -> theme.coversInstant(now))
                    .min(java.util.Comparator.comparing(ThemeDefinition::start));
        }
        String forced = normalized;
        return themes.stream().filter(theme -> theme.id().equals(forced)).findFirst();
    }

    public static boolean isValidMode(String mode, Collection<ThemeDefinition> themes) {
        if (mode == null) return false;
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return normalized.equals(AUTO) || normalized.equals(OFF)
                || themes.stream().anyMatch(theme -> theme.id().equals(normalized));
    }
}
