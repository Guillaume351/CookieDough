package com.cookiebuild.cookiedough.player;

public enum PlayerState {
    LOBBY,
    /** Player owns a slot in an open game but the match has not started yet. */
    QUEUED,
    IN_GAME,
    SPECTATING,
    /** Player is owned by a long-lived activity such as Skyblock, never Quick Play. */
    PERSISTENT_MODE,
    OFFLINE
}
