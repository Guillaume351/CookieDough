package com.cookiebuild.cookiedough.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.util.Date;
import java.util.UUID;

/**
 * Represents a chat message
 * Used for censorship and formatting
 */
@Entity
public class ChatMessage {
    @Id
    @GeneratedValue
    private long id;

    // Relation with PlayerData
    private UUID sender;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private Date sentAt;

    @Column(nullable = false)
    private String playerWorld;

    public ChatMessage() {

    }

    public ChatMessage(UUID sender, String playerWorld, String message) {
        this.sender = sender;
        this.playerWorld = playerWorld;
        this.message = message;
        this.sentAt = new Date();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return message;
    }
}
