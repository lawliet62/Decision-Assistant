package com.decisionassistant.guidance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidate_suggestions")
public class CandidateSuggestion {
    public enum Status { PROPOSED, ACCEPTED, DISMISSED }

    @Id private UUID id;
    @Column(name = "decision_id", nullable = false) private UUID decisionId;
    @Column(nullable = false, length = 300) private String name;
    @Column(nullable = false, length = 2048) private String url;
    @Column(nullable = false, columnDefinition = "text") private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(length = 100) private String provider;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected CandidateSuggestion() {}
    public CandidateSuggestion(UUID decisionId, String name, String url, String reason, String provider, Instant now) {
        this.id = UUID.randomUUID(); this.decisionId = decisionId; this.name = name; this.url = url;
        this.reason = reason; this.provider = provider; this.status = Status.PROPOSED; this.createdAt = now;
    }
    public void accept() { status = Status.ACCEPTED; }
    public void dismiss() { status = Status.DISMISSED; }
    public UUID id() { return id; }
    public UUID decisionId() { return decisionId; }
    public String name() { return name; }
    public String url() { return url; }
    public String reason() { return reason; }
    public Status status() { return status; }
    public String provider() { return provider; }
}
