package com.cookiebuild.cookiedough.model;

import java.util.Date;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "cosmetic_entitlements")
@IdClass(CosmeticEntitlementId.class)
public class CosmeticEntitlement {
    @Id
    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Id
    @Column(name = "cosmetic_id", nullable = false, length = 64)
    private String cosmeticId;

    @Id
    @Column(name = "source", nullable = false, length = 128)
    private String source;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "granted_at", nullable = false)
    private Date grantedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "revoked_at")
    private Date revokedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "expires_at")
    private Date expiresAt;

    public CosmeticEntitlement() {
    }

    public CosmeticEntitlement(
            UUID playerId, String cosmeticId, String source, Date grantedAt, Date expiresAt) {
        this.playerId = playerId;
        this.cosmeticId = cosmeticId;
        this.source = source;
        this.grantedAt = new Date(grantedAt.getTime());
        this.expiresAt = expiresAt == null ? null : new Date(expiresAt.getTime());
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getCosmeticId() {
        return cosmeticId;
    }

    public String getSource() {
        return source;
    }

    public Date getGrantedAt() {
        return grantedAt == null ? null : new Date(grantedAt.getTime());
    }

    public Date getRevokedAt() {
        return revokedAt == null ? null : new Date(revokedAt.getTime());
    }

    public Date getExpiresAt() {
        return expiresAt == null ? null : new Date(expiresAt.getTime());
    }

    public boolean isActive(Date at) {
        return revokedAt == null && (expiresAt == null || expiresAt.after(at));
    }

    public void renew(Date at, Date expiresAt) {
        if (revokedAt != null) return;
        if (grantedAt == null || at.before(grantedAt)) {
            this.grantedAt = new Date(at.getTime());
        }
        if (this.expiresAt == null || expiresAt == null) {
            this.expiresAt = null;
        } else if (expiresAt.after(this.expiresAt)) {
            this.expiresAt = new Date(expiresAt.getTime());
        }
    }

    public void revoke(Date at) {
        this.revokedAt = new Date(at.getTime());
    }
}
