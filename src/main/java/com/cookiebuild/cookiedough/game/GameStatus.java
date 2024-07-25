package com.cookiebuild.cookiedough.game;

import com.cookiebuild.cookiedough.player.CookiePlayer;

public interface GameStatus {
    int getGameId();

    boolean isGameEnded();

    int getPlayerCount();

    boolean addPlayerToAvailableTeam(CookiePlayer player);

    String getGameName();
}