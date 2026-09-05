package com.cookiebuild.cookiedough.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import com.cookiebuild.cookiedough.cosmetics.CosmeticSlot;

public class CosmeticSelectionId implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID playerId;
    private CosmeticSlot slot;

    public CosmeticSelectionId() {
    }

    public CosmeticSelectionId(UUID playerId, CosmeticSlot slot) {
        this.playerId = playerId;
        this.slot = slot;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public CosmeticSlot getSlot() {
        return slot;
    }

    public void setSlot(CosmeticSlot slot) {
        this.slot = slot;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CosmeticSelectionId that)) return false;
        return Objects.equals(playerId, that.playerId) && slot == that.slot;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, slot);
    }
}
