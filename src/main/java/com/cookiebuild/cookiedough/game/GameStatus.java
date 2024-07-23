package com.cookiebuild.cookiedough.game;

import com.cookiebuild.cookiedough.player.CookiePlayer;

public interface GameStatus {
    int getGameNumber();

    boolean isGameEnded();

    int getPlayerCount();

    boolean addPlayerToAvailableTeam(CookiePlayer player);
}