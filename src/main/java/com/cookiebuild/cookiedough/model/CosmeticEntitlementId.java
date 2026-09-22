package com.cookiebuild.cookiedough.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CosmeticEntitlementId implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID playerId;
    private String cosmeticId;
    private String source;

    public CosmeticEntitlementId() {
    }

    public CosmeticEntitlementId(UUID playerId, String cosmeticId, String source) {
        this.playerId = playerId;
        this.cosmeticId = cosmeticId;
        this.source = source;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public String getCosmeticId() {
        return cosmeticId;
    }

    public void setCosmeticId(String cosmeticId) {
        this.cosmeticId = cosmeticId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CosmeticEntitlementId that)) return false;
        return Objects.equals(playerId, that.playerId)
                && Objects.equals(cosmeticId, that.cosmeticId)
                && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, cosmeticId, source);
    }
}
