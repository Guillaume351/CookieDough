package com.cookiebuild.cookiedough.game;

import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import org.bukkit.Bukkit;

import java.util.ArrayList;

public class GameManager {
    static ArrayList<Game> games = new ArrayList<>();

    public static void addGame(Game game) {
        games.add(game);
    }

    public static void removeGame(Game game) {
        games.remove(game);
        // unload game's map
        Bukkit.unloadWorld("game_maps/" + game.getGameId(), false);
    }

    public static ArrayList<Game> getGames() {
        return games;
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
        for (Game game : GameManager.getGames()) {
            if (game.getGameName().equals(gameName)) {
                return game;
            }
        }
        return null;
    }

    public static int getAvailablePlayerCount() {
        return (int) PlayerManager.getPlayers().stream().filter(p -> p.getState() == PlayerState.LOBBY).count();
    }
}
