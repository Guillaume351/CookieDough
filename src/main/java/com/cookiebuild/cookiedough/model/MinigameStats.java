package com.cookiebuild.cookiedough.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "minigame_stats")
@IdClass(MinigameStatsId.class)
public class MinigameStats {

    @Id
    @Column(name = "player_id")
    private UUID playerId;

    @Id
    @Column(name = "minigame")
    private String minigame;

    @Column(name = "level", nullable = false)
    private int level = 1;

    @Column(name = "experience", nullable = false)
    private int experience = 0;

    @Column(name = "unlocked_kits", columnDefinition = "TEXT")
    private String unlockedKits = "[]";

    @Column(name = "last_selected_kit_name")
    private String lastSelectedKitName;

    @Column(name = "last_selected_kit_level", nullable = false, columnDefinition = "integer default 0")
    private int lastSelectedKitLevel = 0;

    // Constructeurs
    public MinigameStats() {
    }

    public MinigameStats(UUID playerId, String minigame) {
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

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
        // Calculer le niveau basé sur l'expérience
        updateLevelFromExperience();
    }

    public void addExperience(int amount) {
        this.experience += amount;
        updateLevelFromExperience();
    }

    private void updateLevelFromExperience() {
        // Formule simple : niveau = sqrt(experience / 100) + 1
        // Niveau 1: 0-99 XP, Niveau 2: 100-399 XP, Niveau 3: 400-899 XP, etc.
        this.level = (int) Math.sqrt(this.experience / 100.0) + 1;
    }

    public int getExperienceForNextLevel() {
        int nextLevel = this.level + 1;
        return (nextLevel - 1) * (nextLevel - 1) * 100;
    }

    public int getExperienceToNextLevel() {
        return getExperienceForNextLevel() - this.experience;
    }

    public String getUnlockedKits() {
        return unlockedKits;
    }

    public void setUnlockedKits(String unlockedKits) {
        this.unlockedKits = unlockedKits;
    }

    public boolean hasUnlockedKit(String kitName) {
        return unlockedKits != null && unlockedKits.contains("\"" + kitName + "\"");
    }

    public void unlockKit(String kitName) {
        if (unlockedKits == null || unlockedKits.equals("[]")) {
            unlockedKits = "[\"" + kitName + "\"]";
        } else if (!hasUnlockedKit(kitName)) {
            // Ajouter le kit à la liste JSON
            unlockedKits = unlockedKits.substring(0, unlockedKits.length() - 1) + ",\"" + kitName + "\"]";
        }
    }

    public String getLastSelectedKitName() {
        return lastSelectedKitName;
    }

    public void setLastSelectedKitName(String lastSelectedKitName) {
        this.lastSelectedKitName = lastSelectedKitName;
    }

    public int getLastSelectedKitLevel() {
        return lastSelectedKitLevel;
    }

    public void setLastSelectedKitLevel(int lastSelectedKitLevel) {
        this.lastSelectedKitLevel = lastSelectedKitLevel;
    }

}