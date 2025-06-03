package com.cookiebuild.cookiedough.model;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_match_performances")
public class PlayerMatchPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerData player;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int killsInMatch;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int deathsInMatch;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int assistsInMatch; // Optional, good for team games

    @Lob
    @JdbcTypeCode(SqlTypes.JSON) // Or SqlTypes.VARCHAR if storing as simple TEXT and handling JSON manually
    @Column(name = "game_specific_metrics")
    private String gameSpecificMetrics; // Store as JSON string

    public PlayerMatchPerformance() {
    }

    public PlayerMatchPerformance(Match match, PlayerData player) {
        this.match = match;
        this.player = player;
        this.killsInMatch = 0;
        this.deathsInMatch = 0;
        this.assistsInMatch = 0;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public PlayerData getPlayer() {
        return player;
    }

    public void setPlayer(PlayerData player) {
        this.player = player;
    }

    public int getKillsInMatch() {
        return killsInMatch;
    }

    public void setKillsInMatch(int killsInMatch) {
        this.killsInMatch = killsInMatch;
    }

    public int getDeathsInMatch() {
        return deathsInMatch;
    }

    public void setDeathsInMatch(int deathsInMatch) {
        this.deathsInMatch = deathsInMatch;
    }

    public int getAssistsInMatch() {
        return assistsInMatch;
    }

    public void setAssistsInMatch(int assistsInMatch) {
        this.assistsInMatch = assistsInMatch;
    }

    public String getGameSpecificMetrics() {
        return gameSpecificMetrics;
    }

    public void setGameSpecificMetrics(String gameSpecificMetrics) {
        this.gameSpecificMetrics = gameSpecificMetrics;
    }
}