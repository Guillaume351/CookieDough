package com.cookiebuild.cookiedough.model;

import jakarta.persistence.*;
import org.bukkit.entity.Player;

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

    public ChatMessage() {

    }

    public ChatMessage(UUID sender, String message) {
        this.sender = sender;
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
