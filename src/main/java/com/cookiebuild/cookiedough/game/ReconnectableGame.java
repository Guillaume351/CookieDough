package com.cookiebuild.cookiedough.game;

import java.util.UUID;

import com.cookiebuild.cookiedough.player.CookiePlayer;

/** A live match that owns a bounded, module-validated reconnect reservation. */
public interface ReconnectableGame {
    boolean hasReconnectReservation(UUID playerId);

    boolean reconnect(CookiePlayer player);
}
