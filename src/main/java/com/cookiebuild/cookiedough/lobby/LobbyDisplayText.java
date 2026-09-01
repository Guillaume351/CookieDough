package com.cookiebuild.cookiedough.lobby;

import com.cookiebuild.cookiedough.game.GameState;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class LobbyDisplayText {
    private LobbyDisplayText() {
    }

    static Component gameNpc(String gameName, int totalPlayerCount, GameState state) {
        return Component.text()
                .append(Component.text(gameName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("👥 " + totalPlayerCount, NamedTextColor.AQUA,
                        TextDecoration.BOLD))
                .append(Component.text(" • ", NamedTextColor.WHITE))
                .append(Component.text(stateLabel(state), stateColor(state)))
                .build();
    }

    static Component unavailableGameNpc(String gameName, int totalPlayerCount) {
        return Component.text()
                .append(Component.text(gameName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("👥 " + totalPlayerCount, NamedTextColor.AQUA,
                        TextDecoration.BOLD))
                .append(Component.text(" • ", NamedTextColor.WHITE))
                .append(Component.text("✖", NamedTextColor.RED, TextDecoration.BOLD))
                .build();
    }

    static Component persistentActivityNpc(String activityName, int totalPlayerCount) {
        return Component.text()
                .append(Component.text(activityName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("👥 " + totalPlayerCount, NamedTextColor.AQUA,
                        TextDecoration.BOLD))
                .append(Component.text(" • ", NamedTextColor.WHITE))
                .append(Component.text("✔", NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();
    }

    static Component championHead(String playerName, int wins) {
        return Component.text()
                .append(Component.text("★ ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(playerName, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" • " + wins, NamedTextColor.GREEN))
                .append(Component.text(" ★", NamedTextColor.GOLD, TextDecoration.BOLD))
                .build();
    }

    static Component onlinePlayers(int playerCount, int gamePlayerCount) {
        return Component.text()
                .append(Component.text("👥 " + playerCount, NamedTextColor.GREEN, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("🎮 " + gamePlayerCount, NamedTextColor.GOLD, TextDecoration.BOLD))
                .build();
    }

    private static String stateLabel(GameState state) {
        return switch (state) {
            case LOADING -> "…";
            case OPEN -> "✔";
            case STARTING -> "⏳";
            case RUNNING -> "▶";
            case FINISHED -> "■";
        };
    }

    private static NamedTextColor stateColor(GameState state) {
        return switch (state) {
            case OPEN -> NamedTextColor.GREEN;
            case STARTING -> NamedTextColor.YELLOW;
            case RUNNING -> NamedTextColor.GOLD;
            case LOADING, FINISHED -> NamedTextColor.GRAY;
        };
    }
}
