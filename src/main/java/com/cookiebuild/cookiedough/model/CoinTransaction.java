package com.cookiebuild.cookiedough.model;

import java.util.Date;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "coin_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uq_coin_transaction_player_source",
                columnNames = {"player_id", "source"}),
        indexes = {
                @Index(name = "idx_coin_transaction_player_created", columnList = "player_id,created_at"),
                @Index(name = "idx_coin_transaction_source", columnList = "source")
        })
public class CoinTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerData player;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 180)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    public CoinTransaction() {
    }

    public CoinTransaction(PlayerData player, int amount, String source, Date createdAt) {
        this.player = player;
        this.amount = amount;
        this.source = source;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public PlayerData getPlayer() {
        return player;
    }

    public int getAmount() {
        return amount;
    }

    public String getSource() {
        return source;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
}
