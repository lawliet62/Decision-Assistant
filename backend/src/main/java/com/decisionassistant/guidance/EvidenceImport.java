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
@Table(name = "evidence_imports")
public class EvidenceImport {
    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED }

    @Id private UUID id;
    @Column(name = "decision_id", nullable = false) private UUID decisionId;
    @Column(name = "option_id", nullable = false) private UUID optionId;
    @Column(nullable = false, length = 2048) private String url;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status;
    @Column(length = 500) private String title;
    @Column(name = "content_hash", length = 64) private String contentHash;
    @Column(columnDefinition = "text") private String excerpt;
    @Column(length = 100) private String provider;
    @Column(columnDefinition = "text") private String error;
    @Column(name = "fetched_at") private Instant fetchedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;

    protected EvidenceImport() {}

    public EvidenceImport(UUID decisionId, UUID optionId, String url, Instant now) {
        this.id = UUID.randomUUID();
        this.decisionId = decisionId;
        this.optionId = optionId;
        this.url = url;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void running(Instant now) { status = Status.RUNNING; attemptCount++; updatedAt = now; }
    public void succeeded(String title, String contentHash, String excerpt, String provider, Instant now) {
        this.status = Status.SUCCEEDED;
        this.title = title;
        this.contentHash = contentHash;
        this.excerpt = excerpt;
        this.provider = provider;
        this.error = null;
        this.fetchedAt = now;
        this.updatedAt = now;
    }
    public void failed(String error, String provider, Instant now) {
        this.status = attemptCount < 2 ? Status.PENDING : Status.FAILED;
        this.error = error;
        this.provider = provider;
        this.updatedAt = now;
    }
    public void retry(Instant now) { status = Status.PENDING; attemptCount = 0; error = null; updatedAt = now; }

    public UUID id() { return id; }
    public UUID decisionId() { return decisionId; }
    public UUID optionId() { return optionId; }
    public String url() { return url; }
    public Status status() { return status; }
    public String title() { return title; }
    public String contentHash() { return contentHash; }
    public String excerpt() { return excerpt; }
    public String provider() { return provider; }
    public String error() { return error; }
    public Instant fetchedAt() { return fetchedAt; }
    public Instant createdAt() { return createdAt; }
    public int attemptCount() { return attemptCount; }
}
