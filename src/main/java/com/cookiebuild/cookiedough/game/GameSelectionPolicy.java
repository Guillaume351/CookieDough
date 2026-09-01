package com.cookiebuild.cookiedough.game;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Selects an open queue while concentrating players without permanently
 * favouring the alphabetically last game when every queue is empty.
 */
public final class GameSelectionPolicy {
    private final Map<String, Long> lastSelectedSequence = new HashMap<>();
    private long selectionSequence;

    /**
     * Thread-safe so commands, party callbacks, and future async callers share
     * one deterministic least-recently-selected history.
     */
    public synchronized Game select(Collection<? extends Game> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<Game> eligible = candidates.stream()
                .filter(Objects::nonNull)
                .filter(game -> game.getState() == GameState.OPEN)
                .filter(Game::isAdmissionsOpen)
                .filter(game -> game.getPlayerCount() < game.getCapacity())
                .map(game -> (Game) game)
                .toList();
        if (eligible.isEmpty()) {
            return null;
        }

        boolean hasQueuedPlayers = eligible.stream().anyMatch(game -> game.getPlayerCount() > 0);
        List<Game> pool = hasQueuedPlayers
                ? eligible.stream().filter(game -> game.getPlayerCount() > 0).toList()
                : eligible;

        Comparator<Game> priority = hasQueuedPlayers
                ? Comparator.comparingInt(GameSelectionPolicy::playersNeededToStart)
                        .thenComparing(Comparator.comparingInt(Game::getPlayerCount).reversed())
                : (left, right) -> 0;
        Game selected = pool.stream()
                .min(priority
                        .thenComparingLong(this::lastSelected)
                        .thenComparing(Game::getGameName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(game -> game.getGameId().toString()))
                .orElse(null);
        if (selected != null) {
            lastSelectedSequence.put(key(selected), ++selectionSequence);
        }
        return selected;
    }

    private static int playersNeededToStart(Game game) {
        return Math.max(0, game.getMinimumPlayers() - game.getPlayerCount());
    }

    private long lastSelected(Game game) {
        return lastSelectedSequence.getOrDefault(key(game), Long.MIN_VALUE);
    }

    private static String key(Game game) {
        return game.getGameName().toLowerCase(Locale.ROOT);
    }
}
