package com.meridianair.ams.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user_created", columnList = "user_id, createdAt"),
        @Index(name = "idx_audit_event_created", columnList = "eventType, createdAt"),
        @Index(name = "idx_audit_created", columnList = "createdAt")
})
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    /** Nullable - not every event has an authenticated actor (e.g. a
     * failed login against an email that doesn't exist). */
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String eventType;

    private String eventCategory;

    @Column(nullable = false)
    private String action;

    private String resourceType;
    private String resourceId;

    private String status;

    @Column(length = 1000)
    private String message;

    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {
    }

    private AuditLog(Builder b) {
        this.userId = b.userId;
        this.eventType = b.eventType;
        this.eventCategory = b.eventCategory;
        this.action = b.action;
        this.resourceType = b.resourceType;
        this.resourceId = b.resourceId;
        this.status = b.status;
        this.message = b.message;
        this.ipAddress = b.ipAddress;
        this.userAgent = b.userAgent;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID userId;
        private String eventType;
        private String eventCategory;
        private String action;
        private String resourceType;
        private String resourceId;
        private String status;
        private String message;
        private String ipAddress;
        private String userAgent;

        public Builder userId(UUID v) { this.userId = v; return this; }
        public Builder eventType(String v) { this.eventType = v; return this; }
        public Builder eventCategory(String v) { this.eventCategory = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder resourceType(String v) { this.resourceType = v; return this; }
        public Builder resourceId(String v) { this.resourceId = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder ipAddress(String v) { this.ipAddress = v; return this; }
        public Builder userAgent(String v) { this.userAgent = v; return this; }
        public AuditLog build() { return new AuditLog(this); }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getEventType() { return eventType; }
    public String getEventCategory() { return eventCategory; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
}
