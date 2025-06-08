package com.cookiebuild.cookiedough.model;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class PlayerData {
    @Id
    private UUID id;
    private String name;
    private Date lastLogin;
    private Date createdAt;

    @OneToMany(mappedBy = "playerData", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PlayerSession> playerSessions = new HashSet<>();

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PlayerMatchPerformance> matchPerformances = new HashSet<>();

    // Getters and setters
    public PlayerData() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Date lastLogin) {
        this.lastLogin = lastLogin;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Set<PlayerSession> getPlayerSessions() {
        return playerSessions;
    }

    public void setPlayerSessions(Set<PlayerSession> playerSessions) {
        this.playerSessions = playerSessions;
    }

    public void addPlayerSession(PlayerSession session) {
        playerSessions.add(session);
        session.setPlayerData(this);
    }

    public void removePlayerSession(PlayerSession session) {
        playerSessions.remove(session);
        session.setPlayerData(null);
    }

    /**
     * Calculates the total play time by summing the duration of all sessions.
     * This does not include the current, ongoing session if the player is online.
     * For live total play time, the LobbyScoreboard will handle adding the current
     * session's duration.
     * 
     * @return Total play time in milliseconds from completed sessions.
     */
    public Long getTotalPlayTime() {
        if (playerSessions == null) {
            return 0L;
        }
        return playerSessions.stream()
                .filter(session -> session.getDuration() != null)
                .mapToLong(PlayerSession::getDuration)
                .sum();
    }

    public Set<PlayerMatchPerformance> getMatchPerformances() {
        return matchPerformances;
    }

    public void setMatchPerformances(Set<PlayerMatchPerformance> matchPerformances) {
        this.matchPerformances = matchPerformances;
    }

    public void addMatchPerformance(PlayerMatchPerformance performance) {
        matchPerformances.add(performance);
        performance.setPlayer(this);
    }

    public void removeMatchPerformance(PlayerMatchPerformance performance) {
        matchPerformances.remove(performance);
        performance.setPlayer(null);
    }
}
