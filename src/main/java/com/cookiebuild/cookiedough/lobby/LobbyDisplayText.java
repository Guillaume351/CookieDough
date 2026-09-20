package com.cookiebuild.cookiedough.lobby;

import com.cookiebuild.cookiedough.game.GameState;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class LobbyDisplayText {
    private LobbyDisplayText() {
    }

    static Component gameNpc(String gameName, int totalPlayerCount, int queuePlayerCount, int queueCapacity,
            boolean queueOpen, GameState state, int countdownSeconds) {
        var display = Component.text()
                .append(Component.text(gameName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("👥 " + totalPlayerCount, NamedTextColor.AQUA,
                        TextDecoration.BOLD))
                .append(Component.text(" • ", NamedTextColor.WHITE))
                .append(Component.text("Lobby " + queuePlayerCount + "/" + queueCapacity,
                        queuePlayerCount > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        if (queueOpen && countdownSeconds > 0) {
            display.appendNewline()
                    .append(Component.text("⏳ " + countdownSeconds + "s", NamedTextColor.YELLOW,
                            TextDecoration.BOLD));
        } else {
            display.append(Component.text(" • ", NamedTextColor.WHITE))
                    .append(Component.text(stateLabel(state, queueOpen), stateColor(state, queueOpen)));
        }
        return display.build();
    }

    static Component unavailableGameNpc(String gameName, int totalPlayerCount) {
        return Component.text()
                .append(Component.text(gameName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("👥 " + totalPlayerCount, NamedTextColor.AQUA,
                        TextDecoration.BOLD))
                .append(Component.text(" • ", NamedTextColor.WHITE))
                .append(Component.text("Lobby 0/-", NamedTextColor.GRAY))
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

    private static String stateLabel(GameState state, boolean queueOpen) {
        if (state == GameState.OPEN && !queueOpen) {
            return "✖";
        }
        return switch (state) {
            case LOADING -> "…";
            case OPEN -> "✔";
            case STARTING -> "⏳";
            case RUNNING -> "▶";
            case FINISHED -> "■";
        };
    }

    private static NamedTextColor stateColor(GameState state, boolean queueOpen) {
        if (state == GameState.OPEN && !queueOpen) {
            return NamedTextColor.RED;
        }
        return switch (state) {
            case OPEN -> NamedTextColor.GREEN;
            case STARTING -> NamedTextColor.YELLOW;
            case RUNNING -> NamedTextColor.GOLD;
            case LOADING, FINISHED -> NamedTextColor.GRAY;
        };
    }
}
