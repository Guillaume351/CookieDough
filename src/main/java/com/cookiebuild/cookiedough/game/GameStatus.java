package com.cookiebuild.cookiedough.game;

import com.cookiebuild.cookiedough.player.CookiePlayer;

import java.util.UUID;

public interface GameStatus {
    UUID getGameId();

    boolean isGameEnded();

    int getPlayerCount();

    boolean addPlayerToAvailableTeam(CookiePlayer player);

    String getGameName();
}