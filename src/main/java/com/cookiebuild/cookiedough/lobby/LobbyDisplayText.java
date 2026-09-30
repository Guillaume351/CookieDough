package com.cookiebuild.cookiedough.lobby;

import java.util.Locale;

import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Shared lobby nameplates. Entity names are the same for every viewer, so they
 * use one configured display locale and only characters that the Bedrock
 * font renders (no emoji: they appear as empty boxes on Geyser clients).
 */
final class LobbyDisplayText {
    static final String SEPARATOR = " · ";

    private LobbyDisplayText() {
    }

    static Component gameNpc(String displayName, boolean featured, int totalPlayerCount, int queuePlayerCount,
            int queueCapacity, int minimumPlayers, boolean queueOpen, GameState state, int countdownSeconds,
            Locale locale) {
        var display = header(displayName, featured, locale);
        display.appendNewline();
        if (queueOpen && countdownSeconds > 0) {
            display.append(Component.text(message("lobby.npc.status.countdown", locale,
                    countdownSeconds, queuePlayerCount, queueCapacity), NamedTextColor.GREEN, TextDecoration.BOLD));
            return display.build();
        }
        if (queueOpen && state == GameState.OPEN) {
            int threshold = Math.max(2, minimumPlayers);
            if (queuePlayerCount <= 0) {
                display.append(Component.text(message("lobby.npc.status.starts_at", locale, threshold),
                        NamedTextColor.AQUA, TextDecoration.BOLD));
            } else if (queuePlayerCount < threshold) {
                display.append(Component.text(message("lobby.npc.status.waiting", locale,
                        queuePlayerCount, threshold - queuePlayerCount), NamedTextColor.GREEN, TextDecoration.BOLD));
            } else {
                display.append(Component.text(message("lobby.npc.status.ready", locale, queuePlayerCount),
                        NamedTextColor.GREEN, TextDecoration.BOLD));
            }
            appendInGame(display, totalPlayerCount - queuePlayerCount, locale);
            return display.build();
        }
        display.append(Component.text(message(statusKey(state, queueOpen), locale), stateColor(state, queueOpen)));
        appendInGame(display, totalPlayerCount, locale);
        return display.build();
    }

    static Component unavailableGameNpc(String displayName, boolean featured, int totalPlayerCount, Locale locale) {
        var display = header(displayName, featured, locale);
        display.appendNewline()
                .append(Component.text(message("lobby.npc.status.preparing", locale), NamedTextColor.GRAY));
        appendInGame(display, totalPlayerCount, locale);
        return display.build();
    }

    static Component persistentActivityNpc(String displayName, int totalPlayerCount, Locale locale) {
        var display = Component.text()
                .append(Component.text(displayName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text(message("lobby.npc.solo_ready", locale), NamedTextColor.GREEN,
                        TextDecoration.BOLD));
        if (totalPlayerCount > 0) {
            display.append(Component.text(SEPARATOR, NamedTextColor.WHITE))
                    .append(Component.text(message("lobby.npc.online", locale, totalPlayerCount),
                            NamedTextColor.AQUA));
        }
        return display.build();
    }

    static Component championHead(String playerName, int wins) {
        return Component.text()
                .append(Component.text("★ ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(playerName, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" • " + wins, NamedTextColor.GREEN))
                .append(Component.text(" ★", NamedTextColor.GOLD, TextDecoration.BOLD))
                .build();
    }

    static Component onlinePlayers(int playerCount, int gamePlayerCount, Locale locale) {
        var display = Component.text()
                .append(Component.text(message("lobby.online.players", locale, playerCount),
                        NamedTextColor.GREEN, TextDecoration.BOLD));
        if (gamePlayerCount > 0) {
            display.appendNewline()
                    .append(Component.text(message("lobby.npc.in_game", locale, gamePlayerCount),
                            NamedTextColor.GOLD, TextDecoration.BOLD));
        }
        return display.build();
    }

    private static net.kyori.adventure.text.TextComponent.Builder header(String displayName, boolean featured,
            Locale locale) {
        var display = Component.text()
                .append(Component.text(displayName, NamedTextColor.GOLD, TextDecoration.BOLD));
        if (featured) {
            display.appendNewline()
                    .append(Component.text(message("lobby.npc.featured", locale), NamedTextColor.YELLOW));
        }
        return display;
    }

    private static void appendInGame(net.kyori.adventure.text.TextComponent.Builder display, int inGame,
            Locale locale) {
        if (inGame <= 0) return;
        display.append(Component.text(SEPARATOR, NamedTextColor.WHITE))
                .append(Component.text(message("lobby.npc.in_game", locale, inGame), NamedTextColor.AQUA));
    }

    private static String statusKey(GameState state, boolean queueOpen) {
        if (state == GameState.OPEN && !queueOpen) {
            return "lobby.npc.status.closed";
        }
        return switch (state) {
            case LOADING, OPEN -> "lobby.npc.status.preparing";
            case STARTING, RUNNING -> "lobby.npc.status.running";
            case FINISHED -> "lobby.npc.status.ending";
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

    private static String message(String key, Locale locale, Object... arguments) {
        return LocaleManager.getMessage(key, locale, arguments);
    }
}
