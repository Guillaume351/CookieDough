package com.cookiebuild.cookiedough.model;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "matches", indexes = {
        @Index(name = "idx_matches_game_start", columnList = "gameType,startTime")
})
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToMany
    @JoinTable(name = "match_players", joinColumns = @JoinColumn(name = "match_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id"),
            indexes = @Index(name = "idx_match_players_player_match", columnList = "player_id,match_id"))
    private Set<PlayerData> players = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "match_winners", joinColumns = @JoinColumn(name = "match_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id"),
            indexes = @Index(name = "idx_match_winners_player_match", columnList = "player_id,match_id"))
    private Set<PlayerData> winners = new HashSet<>();

    @Column(nullable = false)
    private Date startTime;

    @Column()
    private Date endTime;

    @Column(nullable = false)
    private String gameType;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<PlayerMatchPerformance> performances = new HashSet<>();

    // Constructors, getters, and setters

    public Match() {
    }

    public Match(Date startTime, String gameType) {
        this.startTime = startTime;
        this.gameType = gameType;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Set<PlayerData> getPlayers() {
        return players;
    }

    public void setPlayers(Set<PlayerData> players) {
        this.players = players;
    }

    public Set<PlayerData> getWinners() {
        return winners;
    }

    public void setWinners(Set<PlayerData> winners) {
        this.winners = winners;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public Set<PlayerMatchPerformance> getPerformances() {
        return performances;
    }

    public void setPerformances(Set<PlayerMatchPerformance> performances) {
        this.performances = performances;
    }

    public void addPerformance(PlayerMatchPerformance performance) {
        this.performances.add(performance);
        performance.setMatch(this);
    }

    public void removePerformance(PlayerMatchPerformance performance) {
        this.performances.remove(performance);
        performance.setMatch(null);
    }
}
