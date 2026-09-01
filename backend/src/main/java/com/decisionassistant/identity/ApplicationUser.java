package com.decisionassistant.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class ApplicationUser {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "email_normalized", nullable = false, unique = true, length = 320)
    private String emailNormalized;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    @Column(name = "reminder_emails_enabled", nullable = false)
    private boolean reminderEmailsEnabled;

    protected ApplicationUser() {
    }

    public ApplicationUser(UUID id, String email, String emailNormalized, String displayName, Instant now) {
        this.id = id;
        this.email = email;
        this.emailNormalized = emailNormalized;
        this.displayName = displayName;
        this.createdAt = now;
        this.lastLoginAt = now;
        this.reminderEmailsEnabled = true;
    }

    public void login(String email, String emailNormalized, String displayName, Instant now) {
        this.email = email;
        this.emailNormalized = emailNormalized;
        this.displayName = displayName;
        this.lastLoginAt = now;
    }

    public UUID id() { return id; }
    public String email() { return email; }
    public String emailNormalized() { return emailNormalized; }
    public String displayName() { return displayName; }
    public Instant createdAt() { return createdAt; }
    public Instant lastLoginAt() { return lastLoginAt; }
    public boolean reminderEmailsEnabled() { return reminderEmailsEnabled; }
    public void reminderEmailsEnabled(boolean enabled) { this.reminderEmailsEnabled = enabled; }
}
