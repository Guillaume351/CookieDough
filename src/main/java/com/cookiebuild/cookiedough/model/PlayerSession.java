package com.cookiebuild.cookiedough.model;

import java.util.Date;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_sessions", indexes = {
        @Index(name = "idx_player_sessions_player_start", columnList = "player_id,start_time")
})
public class PlayerSession {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerData playerData;

    @Column(name = "start_time", nullable = false)
    private Date startTime;

    @Column(name = "end_time")
    private Date endTime; // Nullable, as session might be ongoing or crashed

    @Column(name = "duration")
    private Long duration; // Duration in milliseconds, updated periodically and on quit

    @Column(name = "server_crash")
    private boolean serverCrash = false; // To mark sessions potentially ended by a crash

    public PlayerSession() {
        this.id = UUID.randomUUID();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    public void setPlayerData(PlayerData playerData) {
        this.playerData = playerData;
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

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public boolean isServerCrash() {
        return serverCrash;
    }

    public void setServerCrash(boolean serverCrash) {
        this.serverCrash = serverCrash;
    }

    // Convenience method to update duration based on start and end times
    public void calculateAndSetDuration() {
        if (this.startTime != null && this.endTime != null) {
            this.duration = this.endTime.getTime() - this.startTime.getTime();
        } else if (this.startTime != null) {
            // If session is ongoing or crashed without an end time,
            // calculate duration up to now (or last known point for periodic save)
            this.duration = new Date().getTime() - this.startTime.getTime();
        } else {
            this.duration = 0L;
        }
    }
}
