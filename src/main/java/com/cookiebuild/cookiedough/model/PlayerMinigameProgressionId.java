package com.cookiebuild.cookiedough.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PlayerMinigameProgressionId implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID playerId;
    private String minigame;

    public PlayerMinigameProgressionId() {
    }

    public PlayerMinigameProgressionId(UUID playerId, String minigame) {
        this.playerId = playerId;
        this.minigame = minigame;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public String getMinigame() {
        return minigame;
    }

    public void setMinigame(String minigame) {
        this.minigame = minigame;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PlayerMinigameProgressionId that = (PlayerMinigameProgressionId) o;
        return Objects.equals(playerId, that.playerId) && Objects.equals(minigame, that.minigame);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, minigame);
    }
}