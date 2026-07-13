package com.cookiebuild.cookiedough.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;

public class GameManager {
    private static final List<Game> games = new CopyOnWriteArrayList<>();

    public static void addGame(Game game) {
        games.add(game);
    }

    public static void removeGame(Game game) {
        games.remove(game);
        // Each game module owns its namespaced world and performs cleanup itself.
    }

    public static ArrayList<Game> getGames() {
        return new ArrayList<>(games);
    }

    public static void tickGames() {
        // Create a copy of the games list to iterate over
        ArrayList<Game> gamesCopy = new ArrayList<>(games);
        for (Game game : gamesCopy) {
            game.tick();
        }
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

    public static Game getOpenGameByName(String gameName) {
        return games.stream()
                .filter(game -> game.getGameName().equalsIgnoreCase(gameName))
                .filter(game -> game.getState() == GameState.OPEN)
                .filter(game -> game.getPlayerCount() < game.getCapacity())
                .max(Comparator.comparingInt(Game::getPlayerCount))
                .orElse(null);
    }

    /** Concentrates low population in the game that is closest to starting. */
    public static Game getBestOpenGame() {
        return games.stream()
                .filter(game -> game.getState() == GameState.OPEN)
                .filter(game -> game.getPlayerCount() < game.getCapacity())
                .max(Comparator.comparingInt(Game::getPlayerCount)
                        .thenComparing(Game::getGameName, String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
    }

    public static int getAvailablePlayerCount() {
        return (int) PlayerManager.getPlayers().stream().filter(p -> p.getState() == PlayerState.LOBBY).count();
    }
}
