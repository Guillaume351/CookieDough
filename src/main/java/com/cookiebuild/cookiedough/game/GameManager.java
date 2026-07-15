package com.cookiebuild.cookiedough.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;

import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.lobby.StatueManager;

public class GameManager {
    public record GameLifecycleEvent(String kind, UUID gameId, String gameName, GameState state) { }

    @FunctionalInterface
    public interface GameLifecycleListener {
        void onGameLifecycle(GameLifecycleEvent event);
    }

    private static final List<Game> games = new CopyOnWriteArrayList<>();
    private static volatile GameLifecycleListener lifecycleListener;
    private static volatile boolean globalAdmissionsOpen = true;
    private static long lastTickBatchAtNanos;

    public static void addGame(Game game) {
        if (!globalAdmissionsOpen) game.closeAdmissions();
        games.add(game);
        notifyGameChanged(game, "registered");
    }

    public static void removeGame(Game game) {
        games.remove(game);
        notifyGameChanged(game, "removed");
        if (game != null && game.getState() == GameState.FINISHED) {
            StatueManager.refreshAfterMatch(game.getGameName());
        }
        // Each game module owns its namespaced world and performs cleanup itself.
    }

    public static ArrayList<Game> getGames() {
        return new ArrayList<>(games);
    }

    public static void tickGames() {
        long batchStartedAt = System.nanoTime();
        if (lastTickBatchAtNanos != 0L) {
            long schedulingDelayMillis = Math.max(0L,
                    (batchStartedAt - lastTickBatchAtNanos) / 1_000_000L - 1_000L);
            if (schedulingDelayMillis >= 100L) {
                CookieDough.getInstance().getLogger().warning(
                        "[performance] event=server_tick_delay delay_ms=" + schedulingDelayMillis);
            }
        }
        lastTickBatchAtNanos = batchStartedAt;
        // Create a copy of the games list to iterate over
        ArrayList<Game> gamesCopy = new ArrayList<>(games);
        for (Game game : gamesCopy) {
            long startedAt = System.nanoTime();
            game.tick();
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            if (elapsedMillis >= 50L) {
                CookieDough.getInstance().getLogger().warning(
                        "[performance] event=slow_game_tick game=" + game.getGameName()
                                + " state=" + game.getState()
                                + " elapsed_ms=" + elapsedMillis);
            }
        }
    }

    static boolean hasOtherOpenGame(Game current) {
        return games.stream()
                .anyMatch(game -> game != current
                        && game.getState() == GameState.OPEN
                        && game.isAdmissionsOpen()
                        && game.getGameName().equalsIgnoreCase(current.getGameName()));
    }

    public static Game getGameOfPlayer(CookiePlayer player) {
        for (Game game : GameManager.getGames()) {
            if (game.getPlayers().contains(player)) {
                return game;
            }
        }
        return null;
    }

    public static Game getGameByName(String gameName) {
        Game open = getOpenGameByName(gameName);
        if (open != null) {
            return open;
        }
        return games.stream().filter(game -> game.getGameName().equalsIgnoreCase(gameName))
                .findFirst().orElse(null);
    }

    public static Game getGameById(UUID gameId) {
        return games.stream().filter(game -> game.getGameId().equals(gameId)).findFirst().orElse(null);
    }

    public static void setLifecycleListener(GameLifecycleListener listener) {
        lifecycleListener = listener;
    }

    public static void clearLifecycleListener(GameLifecycleListener listener) {
        if (lifecycleListener == listener) lifecycleListener = null;
    }

    public static boolean areGlobalAdmissionsOpen() {
        return globalAdmissionsOpen;
    }

    public static void setGlobalAdmissionsOpen(boolean open) {
        globalAdmissionsOpen = open;
        for (Game game : games) {
            if (!open) game.closeAdmissions();
            else if (game.getState() == GameState.OPEN) game.reopenAdmissions();
        }
    }

    static void notifyGameChanged(Game game, String kind) {
        GameLifecycleListener listener = lifecycleListener;
        if (listener == null || game == null) return;
        listener.onGameLifecycle(new GameLifecycleEvent(kind, game.getGameId(), game.getGameName(), game.getState()));
    }

    public static Game getOpenGameByName(String gameName) {
        return games.stream()
                .filter(game -> game.getGameName().equalsIgnoreCase(gameName))
                .filter(game -> game.getState() == GameState.OPEN)
                .filter(Game::isAdmissionsOpen)
                .filter(game -> game.getPlayerCount() < game.getCapacity())
                .max(Comparator.comparingInt(Game::getPlayerCount))
                .orElse(null);
    }

    /** Concentrates low population in the game that is closest to starting. */
    public static Game getBestOpenGame() {
        return games.stream()
                .filter(game -> game.getState() == GameState.OPEN)
                .filter(Game::isAdmissionsOpen)
                .filter(game -> game.getPlayerCount() < game.getCapacity())
                .max(Comparator.comparingInt(Game::getPlayerCount)
                        .thenComparing(Game::getGameName, String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
    }

    public static int getAvailablePlayerCount() {
        return (int) PlayerManager.getPlayers().stream().filter(p -> p.getState() == PlayerState.LOBBY).count();
    }
}
