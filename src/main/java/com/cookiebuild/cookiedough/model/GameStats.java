package com.cookiebuild.cookiedough.model;

import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_stats")
@Inheritance(strategy = InheritanceType.JOINED)
public class GameStats {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerData player;

    @Column(nullable = false)
    private String gameType;

    @Column(nullable = false)
    private int gamesPlayed;

    @Column(nullable = false)
    private int gamesWon;

    @Column(nullable = false)
    private int gamesLost;

    @Column(nullable = false)
    private int totalKills;

    @Column(nullable = false)
    private int totalDeaths;

    // Constructors
    public GameStats() {
        this.gamesPlayed = 0;
        this.gamesWon = 0;
        this.gamesLost = 0;
        this.totalKills = 0;
        this.totalDeaths = 0;
    }

    public GameStats(PlayerData player, String gameType) {
        this();
        this.player = player;
        this.gameType = gameType;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PlayerData getPlayer() {
        return player;
    }

    public void setPlayer(PlayerData player) {
        this.player = player;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public void setGamesPlayed(int gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }

    public int getGamesWon() {
        return gamesWon;
    }

    public void setGamesWon(int gamesWon) {
        this.gamesWon = gamesWon;
    }

    public int getGamesLost() {
        return gamesLost;
    }

    public void setGamesLost(int gamesLost) {
        this.gamesLost = gamesLost;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void setTotalKills(int totalKills) {
        this.totalKills = totalKills;
    }

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public void setTotalDeaths(int totalDeaths) {
        this.totalDeaths = totalDeaths;
    }

    // Utility methods
    public void incrementGamesPlayed() {
        this.gamesPlayed++;
    }

    public void incrementGamesWon() {
        this.gamesWon++;
    }

    public void incrementGamesLost() {
        this.gamesLost++;
    }

    public void addKills(int kills) {
        this.totalKills += kills;
    }

    public void addDeaths(int deaths) {
        this.totalDeaths += deaths;
    }

    public double getKillDeathRatio() {
        if (totalDeaths == 0) {
            return totalKills;
        }
        return (double) totalKills / totalDeaths;
    }

    public double getWinRate() {
        if (gamesPlayed == 0) {
            return 0;
        }
        return (double) gamesWon / gamesPlayed;
    }

    public Map<String, String> getFormattedSpecificStats() {
        return java.util.Collections.emptyMap(); // Default: no specific stats
    }
}