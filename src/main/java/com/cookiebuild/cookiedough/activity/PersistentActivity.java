package com.cookiebuild.cookiedough.activity;

import java.util.UUID;

import com.cookiebuild.cookiedough.player.CookiePlayer;

/** A long-lived destination that is deliberately not part of match Quick Play. */
public interface PersistentActivity {
    String name();

    boolean isAvailable();

    ActivityAdmissionResult enter(CookiePlayer player);

    /** Returns false only when an interactive leave must be refused to preserve player data. */
    boolean leave(CookiePlayer player, String reason);

    /** Side-effect-free readiness probe used before an atomic queue transition. */
    default boolean canLeave(CookiePlayer player, String reason) { return true; }

    /** Gives an activity a player-facing recovery path when a requested leave is not ready. */
    default void onLeaveBlocked(CookiePlayer player, String reason) { }

    /** Lets a passive activity prepare its player for a later queue admission. */
    default void onQueueIntentRegistered(CookiePlayer player) { }

    boolean owns(UUID playerId);

    /** Identifies a saved persistent destination before player-data initialization completes. */
    default boolean ownsWorld(String worldName) { return false; }
}
