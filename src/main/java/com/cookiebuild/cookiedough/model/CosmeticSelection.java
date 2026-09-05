package com.cookiebuild.cookiedough.model;

import java.util.Date;
import java.util.UUID;

import com.cookiebuild.cookiedough.cosmetics.CosmeticSlot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "cosmetic_selections")
@IdClass(CosmeticSelectionId.class)
public class CosmeticSelection {
    @Id
    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "slot", nullable = false, length = 32)
    private CosmeticSlot slot;

    @Column(name = "cosmetic_id", nullable = false, length = 64)
    private String cosmeticId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "selected_at", nullable = false)
    private Date selectedAt;

    public CosmeticSelection() {
    }

    public CosmeticSelection(UUID playerId, CosmeticSlot slot, String cosmeticId, Date selectedAt) {
        this.playerId = playerId;
        this.slot = slot;
        select(cosmeticId, selectedAt);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public CosmeticSlot getSlot() {
        return slot;
    }

    public String getCosmeticId() {
        return cosmeticId;
    }

    public Date getSelectedAt() {
        return selectedAt == null ? null : new Date(selectedAt.getTime());
    }

    public void select(String cosmeticId, Date at) {
        this.cosmeticId = cosmeticId;
        this.selectedAt = new Date(at.getTime());
    }
}
