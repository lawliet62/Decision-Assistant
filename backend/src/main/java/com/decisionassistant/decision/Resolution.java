package com.decisionassistant.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import static com.decisionassistant.decision.DecisionTypes.ResolutionKind;

@Entity
@Table(name = "resolutions")
public class Resolution {

    @Id
    private UUID id;

    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResolutionKind kind;

    @Column(name = "selected_option_id")
    private UUID selectedOptionId;

    @Column(columnDefinition = "text")
    private String rationale;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Resolution() {
    }

    public Resolution(UUID decisionId, ResolutionKind kind, UUID selectedOptionId, String rationale, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.decisionId = decisionId;
        this.kind = kind;
        this.selectedOptionId = selectedOptionId;
        this.rationale = rationale;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID decisionId() { return decisionId; }
    public ResolutionKind kind() { return kind; }
    public UUID selectedOptionId() { return selectedOptionId; }
    public String rationale() { return rationale; }
    public Instant createdAt() { return createdAt; }
}
