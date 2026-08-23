package com.cookiebuild.cookiedough.lobby;

import java.util.Comparator;
import java.util.List;

import com.cookiebuild.cookiedough.activity.ActivityRegistry;
import com.cookiebuild.cookiedough.activity.PersistentActivity;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;

/** One source of truth for population and availability across matches and persistent activities. */
public final class ModePopulationService {
    public record Snapshot(int players, GameState state, boolean available, boolean persistent) {
        public String stateKey() {
            if (!available) return "hub.games.state.offline";
            if (persistent) return "hub.games.state.open";
            return switch (state) {
                case OPEN -> "hub.games.state.join";
                case STARTING -> "hub.games.state.starting";
                case RUNNING -> "hub.games.state.running";
                case LOADING -> "hub.games.state.loading";
                case FINISHED -> "hub.games.state.ending";
            };
        }
    }

    private ModePopulationService() { }

    public static Snapshot snapshot(String modeName) {
        PersistentActivity activity = ActivityRegistry.find(modeName);
        if (activity != null) {
            return new Snapshot(LobbyModePlayerCounter.forPersistentActivity(modeName), GameState.OPEN,
                    activity.isAvailable(), true);
        }
        List<Game> arenas = GameManager.getGames().stream()
                .filter(game -> game.getGameName().equalsIgnoreCase(modeName))
                .toList();
        GameState state = arenas.stream().map(Game::getState)
                .min(Comparator.comparingInt(ModePopulationService::statePriority))
                .orElse(GameState.LOADING);
        boolean available = arenas.stream().anyMatch(game -> game.getState() == GameState.OPEN);
        return new Snapshot(GameManager.getOnlineGamePlayerCount(modeName), state, available, false);
    }

    public static int totalActivePlayers() {
        return GamePresentation.games().stream().map(GamePresentation::gameName)
                .map(ModePopulationService::snapshot)
                .mapToInt(Snapshot::players)
                .sum();
    }

    public static boolean hasReadyMatchForOneMorePlayer() {
        return GameManager.getGames().stream().anyMatch(game -> game.getState() == GameState.OPEN
                && game.getPlayerCount() + 1 >= game.getMinimumPlayers());
    }

    private static int statePriority(GameState state) {
        return switch (state) {
            case OPEN -> 0;
            case STARTING -> 1;
            case RUNNING -> 2;
            case LOADING -> 3;
            case FINISHED -> 4;
        };
    }
}
