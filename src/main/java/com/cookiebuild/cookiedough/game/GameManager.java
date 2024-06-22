package com.cookiebuild.cookiedough.game;

import com.cookiebuild.cookiedough.player.CookiePlayer;

import java.util.ArrayList;

public class GameManager {
    static ArrayList<Game> games = new ArrayList<>();

    public static void addGame(Game game) {
        games.add(game);
    }

    public static void removeGame(Game game) {
        games.remove(game);
    }

    public static ArrayList<Game> getGames() {
        return games;
    }

    public static Game getGameOfPlayer(CookiePlayer player) {
        for (Game game : GameManager.getGames()) {
            if (game.getPlayers().contains(player)) {
                return game;
            }
        }
        return null;
    }
}
