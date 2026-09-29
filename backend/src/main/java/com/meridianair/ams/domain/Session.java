package com.meridianair.ams.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions", indexes = {
        @Index(name = "idx_sessions_user", columnList = "user_id"),
        @Index(name = "idx_sessions_refresh_hash", columnList = "refreshTokenHash"),
        @Index(name = "idx_sessions_expiry", columnList = "expiresAt")
})
public class Session {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Argon2 hash of the refresh token - the raw token is never stored,
     * only ever handed to the client once at issuance. */
    @Column(nullable = false, length = 512)
    private String refreshTokenHash;

    private String deviceName;
    private String userAgent;
    private String ipAddress;

    private Instant createdAt = Instant.now();
    private Instant lastActivityAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    private boolean revoked = false;
    private Instant revokedAt;
    private String revokeReason;

    protected Session() {
    }

    public Session(User user, String refreshTokenHash, String deviceName, String userAgent,
                   String ipAddress, Instant expiresAt) {
        this.user = user;
        this.refreshTokenHash = refreshTokenHash;
        this.deviceName = deviceName;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.expiresAt = expiresAt;
    }

    public boolean isValid() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }

    public void revoke(String reason) {
        this.revoked = true;
        this.revokedAt = Instant.now();
        this.revokeReason = reason;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public void setRefreshTokenHash(String refreshTokenHash) { this.refreshTokenHash = refreshTokenHash; }
    public String getDeviceName() { return deviceName; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public void touchActivity() { this.lastActivityAt = Instant.now(); }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isRevoked() { return revoked; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokeReason() { return revokeReason; }
}
