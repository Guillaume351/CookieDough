package com.cookiebuild.cookiedough.model;

import jakarta.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToMany
    @JoinTable(
            name = "match_players",
            joinColumns = @JoinColumn(name = "match_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id")
    )
    private Set<PlayerData> players = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "match_winners",
            joinColumns = @JoinColumn(name = "match_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id")
    )
    private Set<PlayerData> winners = new HashSet<>();

    @Column(nullable = false)
    private Date startTime;

    @Column()
    private Date endTime;

    @Column(nullable = false)
    private String gameType;

    // Constructors, getters, and setters

    public Match() {}

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
}