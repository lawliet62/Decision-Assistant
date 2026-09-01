package com.decisionassistant.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outcome_reminders")
public class OutcomeReminder {
    public enum Status { PENDING, SENT, COMPLETED, SKIPPED, FAILED }
    public enum Channel { EMAIL, IN_APP }

    @Id private UUID id;
    @Column(name = "decision_id", nullable = false) private UUID decisionId;
    @Column(name = "due_at", nullable = false) private Instant dueAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Channel channel;
    @Column(name = "provider_message_id", length = 200) private String providerMessageId;
    @Column(name = "last_error", columnDefinition = "text") private String lastError;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;

    protected OutcomeReminder() {}
    public OutcomeReminder(UUID decisionId, Instant dueAt, Channel channel, Instant now) {
        this.id = UUID.randomUUID(); this.decisionId = decisionId; this.dueAt = dueAt;
        this.channel = channel; this.status = Status.PENDING; this.createdAt = now;
    }
    public void sent(String providerMessageId, Instant now) { status = Status.SENT; this.providerMessageId = providerMessageId; sentAt = now; lastError = null; }
    public void failed(String error, Instant now) {
        attemptCount++;
        lastError = error;
        if (attemptCount < 3) { status = Status.PENDING; dueAt = now.plusSeconds(600L * attemptCount); }
        else status = Status.FAILED;
    }
    public void complete() { status = Status.COMPLETED; }
    public void skip() { status = Status.SKIPPED; }
    public void reschedule(Instant dueAt) { this.dueAt = dueAt; status = Status.PENDING; lastError = null; }
    public UUID id() { return id; }
    public UUID decisionId() { return decisionId; }
    public Instant dueAt() { return dueAt; }
    public Status status() { return status; }
    public Channel channel() { return channel; }
    public String lastError() { return lastError; }
    public Instant sentAt() { return sentAt; }
    public int attemptCount() { return attemptCount; }
}
