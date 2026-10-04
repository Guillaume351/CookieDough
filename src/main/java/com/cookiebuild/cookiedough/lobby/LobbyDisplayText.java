package com.cookiebuild.cookiedough.lobby;

import java.util.Locale;

import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Shared lobby nameplates. Entity names are the same for every viewer, so they
 * use one configured display locale and only characters that the Bedrock
 * font renders (no emoji: they appear as empty boxes on Geyser clients). The
 * separator is U+00B7 (Latin-1), never a symbol from the emoji/dingbat blocks.
 *
 * <p>Every minigame selector keeps the same three short lines so the counters
 * are readable at a glance, like the pre-2026-09-30 plates:
 * <pre>
 * Build Battle · Vedette
 * Lobby 1/8 · départ dans 24 s
 * 3 en jeu
 * </pre>
 */
final class LobbyDisplayText {
    static final String SEPARATOR = " · ";

    /**
     * Live counters behind one minigame selector, sampled once per refresh.
     *
     * @param queued players waiting in the selected open lobby
     * @param capacity lobby capacity
     * @param minimumPlayers configured headcount needed to count down
     * @param queueOpen whether the selected lobby currently admits players
     * @param state state of the selected arena
     * @param countdownSeconds running countdown, or 0
     * @param soloStartSeconds seconds before a lone player's solo match starts, or -1
     * @param playing players inside live arenas of the mode (queues excluded)
     */
    record QueueSnapshot(int queued, int capacity, int minimumPlayers, boolean queueOpen, GameState state,
            int countdownSeconds, int soloStartSeconds, int playing) {
    }

    private LobbyDisplayText() {
    }

    static Component gameNpc(String displayName, boolean featured, QueueSnapshot queue, Locale locale) {
        var display = header(displayName, featured, locale);
        display.appendNewline();
        int queued = queue.queueOpen() ? Math.max(0, queue.queued()) : 0;
        display.append(Component.text(message("lobby.npc.lobby", locale, queued, Math.max(0, queue.capacity())),
                        queued > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(SEPARATOR, NamedTextColor.GRAY))
                .append(lobbyState(queue, queued, locale));
        appendPlaying(display, queue.playing(), locale);
        return display.build();
    }

    /** Selector of a mode without any registered arena yet (boot, arena preparation). */
    static Component unavailableGameNpc(String displayName, boolean featured, int playing, Locale locale) {
        var display = header(displayName, featured, locale);
        display.appendNewline()
                .append(Component.text(message("lobby.npc.lobby_preparing", locale), NamedTextColor.GRAY,
                        TextDecoration.BOLD));
        appendPlaying(display, playing, locale);
        return display.build();
    }

    static Component persistentActivityNpc(String displayName, int playing, Locale locale) {
        var display = Component.text()
                .append(Component.text(displayName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text(message("lobby.npc.solo_ready", locale), NamedTextColor.GREEN,
                        TextDecoration.BOLD));
        appendPlaying(display, playing, locale);
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

    /** Global hologram: both counters stay visible, the second one grey at zero. */
    static Component onlinePlayers(int playerCount, int gamePlayerCount, Locale locale) {
        int playing = Math.max(0, gamePlayerCount);
        return Component.text()
                .append(Component.text(message("lobby.online.players", locale, Math.max(0, playerCount)),
                        NamedTextColor.GREEN, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text(message("lobby.npc.in_game", locale, playing),
                        playing > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY, TextDecoration.BOLD))
                .build();
    }

    /**
     * Short state after "Lobby x/y". A solo-start mode (Build Battle) shows when
     * the lone player's match really starts instead of a missing second player.
     */
    private static Component lobbyState(QueueSnapshot queue, int queued, Locale locale) {
        if (!queue.queueOpen()) {
            return queue.state() == GameState.OPEN
                    ? Component.text(message("lobby.npc.state.closed", locale), NamedTextColor.RED)
                    : Component.text(message("lobby.npc.state.preparing", locale), NamedTextColor.GRAY);
        }
        if (queue.countdownSeconds() > 0) {
            return Component.text(message("lobby.npc.state.countdown", locale, queue.countdownSeconds()),
                    NamedTextColor.YELLOW, TextDecoration.BOLD);
        }
        if (queued > 0 && queue.soloStartSeconds() >= 0) {
            return Component.text(message("lobby.npc.state.countdown", locale, queue.soloStartSeconds()),
                    NamedTextColor.YELLOW, TextDecoration.BOLD);
        }
        if (queued == 0) {
            return Component.text(message("lobby.npc.state.open", locale), NamedTextColor.GREEN);
        }
        int missing = Math.max(1, queue.minimumPlayers()) - queued;
        return missing > 0
                ? Component.text(message("lobby.npc.state.missing", locale, missing), NamedTextColor.YELLOW)
                : Component.text(message("lobby.npc.state.ready", locale), NamedTextColor.GREEN);
    }

    private static TextComponent.Builder header(String displayName, boolean featured, Locale locale) {
        var display = Component.text()
                .append(Component.text(displayName, NamedTextColor.GOLD, TextDecoration.BOLD));
        if (featured) {
            display.append(Component.text(SEPARATOR, NamedTextColor.GRAY))
                    .append(Component.text(message("lobby.npc.featured_tag", locale), NamedTextColor.YELLOW));
        }
        return display;
    }

    /** Third line, always present: how many players are in a live match of this mode. */
    private static void appendPlaying(TextComponent.Builder display, int playing, Locale locale) {
        int count = Math.max(0, playing);
        display.appendNewline()
                .append(Component.text(message("lobby.npc.in_game", locale, count),
                        count > 0 ? NamedTextColor.AQUA : NamedTextColor.GRAY));
    }

    private static String message(String key, Locale locale, Object... arguments) {
        return LocaleManager.getMessage(key, locale, arguments);
    }
}
