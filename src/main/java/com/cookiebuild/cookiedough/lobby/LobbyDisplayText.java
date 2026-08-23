package com.cookiebuild.cookiedough.lobby;

import com.cookiebuild.cookiedough.game.GameState;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class LobbyDisplayText {
    private LobbyDisplayText() {
    }

    static Component gameNpc(String gameName, int playerCount, int capacity, GameState state) {
        return Component.text()
                .append(Component.text(gameName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text(playerCount + "/" + capacity, NamedTextColor.AQUA))
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text(stateLabel(state), stateColor(state)))
                .build();
    }

    static Component unavailableGameNpc(String gameName) {
        return Component.text()
                .append(Component.text(gameName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("Unavailable", NamedTextColor.RED))
                .build();
    }

    static Component persistentActivityNpc(String activityName) {
        return Component.text()
                .append(Component.text(activityName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("★ CLICK TO PLAY ★", NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();
    }

    static Component onlinePlayers(int playerCount, int gamePlayerCount) {
        String playerNoun = playerCount == 1 ? "player" : "players";
        String gameNoun = gamePlayerCount == 1 ? "game" : "games";
        return Component.text()
                .append(Component.text(playerCount, NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" " + playerNoun + " online", NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text(gamePlayerCount, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" in " + gameNoun, NamedTextColor.GRAY))
                .build();
    }

    private static String stateLabel(GameState state) {
        return switch (state) {
            case LOADING -> "LOADING";
            case OPEN -> "JOIN";
            case STARTING -> "STARTING";
            case RUNNING -> "IN GAME";
            case FINISHED -> "ENDING";
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
