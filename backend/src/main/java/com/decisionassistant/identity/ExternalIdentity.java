package com.decisionassistant.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_identities")
public class ExternalIdentity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 500)
    private String issuer;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected ExternalIdentity() {
    }

    public ExternalIdentity(UUID userId, String issuer, String subject, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.issuer = issuer;
        this.subject = subject;
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    public void loggedIn(Instant now) {
        this.lastLoginAt = now;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public String issuer() { return issuer; }
    public String subject() { return subject; }
    public Instant createdAt() { return createdAt; }
    public Instant lastLoginAt() { return lastLoginAt; }
}
