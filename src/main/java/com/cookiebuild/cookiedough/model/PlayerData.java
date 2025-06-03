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
