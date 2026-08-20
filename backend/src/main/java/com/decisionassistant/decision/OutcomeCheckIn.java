package com.decisionassistant.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import static com.decisionassistant.decision.DecisionTypes.ExpectationStatus;

@Entity
@Table(name = "outcome_check_ins")
public class OutcomeCheckIn {

    @Id
    private UUID id;

    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;

    @Column(nullable = false)
    private int satisfaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "expectation_status", nullable = false, length = 30)
    private ExpectationStatus expectationStatus;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutcomeCheckIn() {
    }

    public OutcomeCheckIn(UUID decisionId, int satisfaction, ExpectationStatus expectationStatus,
                          String notes, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.decisionId = decisionId;
        this.satisfaction = satisfaction;
        this.expectationStatus = expectationStatus;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID decisionId() { return decisionId; }
    public int satisfaction() { return satisfaction; }
    public ExpectationStatus expectationStatus() { return expectationStatus; }
    public String notes() { return notes; }
    public Instant createdAt() { return createdAt; }
}
