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
        boolean available = arenas.stream().anyMatch(ModePopulationService::canAdmitOne);
        return new Snapshot(GameManager.getOnlineGamePlayerCount(modeName), state, available, false);
    }

    public static int totalActivePlayers() {
        return GamePresentation.games().stream().map(GamePresentation::gameName)
                .map(ModePopulationService::snapshot)
                .mapToInt(Snapshot::players)
                .sum();
    }

    public static boolean hasReadyMatchForOneMorePlayer() {
        return hasReadyMatchForOneMorePlayer(GameManager.getGames());
    }

    static boolean hasReadyMatchForOneMorePlayer(List<? extends Game> games) {
        return games != null && games.stream().anyMatch(game -> {
            int queued = GameManager.getAdmittableQueueIntentCount(game);
            return canAdmitOne(game, queued) && canBecomeReadyWithOneMorePlayer(
                    game.getPlayerCount(), queued, game.getMinimumPlayers(), game.getCapacity());
        });
    }

    private static boolean canAdmitOne(Game game) {
        return canAdmitOne(game, GameManager.getAdmittableQueueIntentCount(game));
    }

    private static boolean canAdmitOne(Game game, int validQueueIntents) {
        return game != null && game.getState() == GameState.OPEN && game.isAdmissionsOpen()
                && game.getPlayerCount() + validQueueIntents < game.getCapacity()
                && game.getPartyAdmissionProblem(1) == null;
    }

    static boolean canBecomeReadyWithOneMorePlayer(int admittedPlayers, int validQueueIntents,
            int minimumPlayers, int capacity) {
        return admittedPlayers >= 0 && validQueueIntents >= 0 && minimumPlayers >= 1
                && capacity >= minimumPlayers && admittedPlayers + validQueueIntents < capacity
                && admittedPlayers + validQueueIntents + 1 >= minimumPlayers;
    }

    public static boolean isPersistentActivityAvailable(String activityName) {
        PersistentActivity activity = ActivityRegistry.find(activityName);
        return activity != null && activity.isAvailable();
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
