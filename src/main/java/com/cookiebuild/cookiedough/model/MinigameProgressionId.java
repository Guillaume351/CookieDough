package com.cookiebuild.cookiedough.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class MinigameProgressionId implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID playerId;
    private String minigame;

    // Constructeur par défaut requis
    public MinigameProgressionId() {
    }

    public MinigameProgressionId(UUID playerId, String minigame) {
        this.playerId = playerId;
        this.minigame = minigame;
    }

    // Getters et Setters
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

    // equals et hashCode requis pour les clés composites
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MinigameProgressionId that = (MinigameProgressionId) o;
        return Objects.equals(playerId, that.playerId) &&
                Objects.equals(minigame, that.minigame);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, minigame);
    }

    @Override
    public String toString() {
        return "MinigameProgressionId{" +
                "playerId=" + playerId +
                ", minigame='" + minigame + '\'' +
                '}';
    }
}