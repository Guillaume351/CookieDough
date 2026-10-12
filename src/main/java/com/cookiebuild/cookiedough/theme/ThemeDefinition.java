package com.cookiebuild.cookiedough.theme;

import java.util.List;

/** Parsed {@code themes/<id>/theme.yml}. */
public record ThemeDefinition(String id, List<String> buildBattleThemes) {
    public ThemeDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("A theme id is required");
        buildBattleThemes = List.copyOf(buildBattleThemes == null ? List.of() : buildBattleThemes);
    }
}
