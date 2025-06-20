package com.cookiebuild.cookiedough.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_minigame_progression")
@IdClass(PlayerMinigameProgressionId.class)
public class PlayerMinigameProgression {

    @Id
    @Column(name = "player_id")
    private UUID playerId;

    @Id
    @Column(name = "minigame")
    private String minigame;

    @Column(name = "level", nullable = false)
    private int level = 1;

    @Column(name = "coins", nullable = false)
    private int coins = 100;

    @Column(name = "experience", nullable = false)
    private int experience = 0;

    @Column(name = "unlocked_kits", columnDefinition = "TEXT")
    private String unlockedKits = "[]";

    // Constructeurs
    public PlayerMinigameProgression() {
    }

    public PlayerMinigameProgression(UUID playerId, String minigame) {
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

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getCoins() {
        return coins;
    }

    public void setCoins(int coins) {
        this.coins = coins;
    }

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public String getUnlockedKits() {
        return unlockedKits;
    }

    public void setUnlockedKits(String unlockedKits) {
        this.unlockedKits = unlockedKits;
    }

    // Méthodes utilitaires pour la progression
    public void addExperience(int xp) {
        this.experience += xp;
        checkLevelUp();
    }

    public void addCoins(int amount) {
        this.coins += amount;
    }

    public boolean spendCoins(int amount) {
        if (this.coins >= amount) {
            this.coins -= amount;
            return true;
        }
        return false;
    }

    public int getExperienceForNextLevel() {
        return level * 1000; // 1000 XP par niveau
    }

    public int getExperienceToNextLevel() {
        return getExperienceForNextLevel() - experience;
    }

    private void checkLevelUp() {
        while (experience >= getExperienceForNextLevel()) {
            experience -= getExperienceForNextLevel();
            level++;
        }
    }

    // Méthodes pour les kits débloqués
    public boolean hasUnlockedKit(String kitName) {
        return unlockedKits.contains("\"" + kitName + "\"");
    }

    public void unlockKit(String kitName) {
        if (!hasUnlockedKit(kitName)) {
            if (unlockedKits.equals("[]")) {
                unlockedKits = "[\"" + kitName + "\"]";
            } else {
                unlockedKits = unlockedKits.substring(0, unlockedKits.length() - 1) + ",\"" + kitName + "\"]";
            }
        }
    }
}