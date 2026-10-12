package com.cookiebuild.cookiedough.theme;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.utils.LocaleManager;

/**
 * Seasonal theme switchboard (Halloween, Christmas...). A theme only ever lives
 * in players' clients and in memory: it never edits a world, an archive or a
 * persistent entity, so switching it off restores everything.
 *
 * <p>Public contract used by the cosmetics shop and by game plugins. Every
 * method is safe to call from the main thread and returns an empty value when
 * no theme is active.
 */
public final class ThemeService {
    /** Id of the Halloween theme, also the {@code season} of its limited cosmetics. */
    public static final String HALLOWEEN = "halloween";

    private volatile ThemeDefinition active;

    public Optional<String> activeThemeId() {
        ThemeDefinition current = active;
        return current == null ? Optional.empty() : Optional.of(current.id());
    }

    public Optional<ThemeDefinition> active() {
        return Optional.ofNullable(active);
    }

    public boolean isActive(String themeId) {
        ThemeDefinition current = active;
        return current != null && current.id().equalsIgnoreCase(themeId);
    }

    /** Seasons whose limited cosmetics are on sale right now. */
    public Set<String> activeSeasonIds() {
        ThemeDefinition current = active;
        return current == null ? Set.of() : Set.of(current.id());
    }

    /**
     * Canonical (English) Build Battle themes to feature while the theme is
     * active. Build Battle keeps one of them among the vote candidates.
     */
    public List<String> buildBattleThemes() {
        ThemeDefinition current = active;
        return current == null ? List.of() : current.buildBattleThemes();
    }

    /** Seasonal subtitle of the arrival title, when the theme has one. */
    public Optional<String> joinSubtitle(Player player) {
        ThemeDefinition current = active;
        if (current == null) return Optional.empty();
        String key = current.messagePrefix() + ".join.subtitle";
        String text = LocaleManager.getMessage(key, player == null ? null : player.locale());
        return text.equals(key) ? Optional.empty() : Optional.of(text);
    }

    void setActive(ThemeDefinition theme) {
        this.active = theme;
    }
}
