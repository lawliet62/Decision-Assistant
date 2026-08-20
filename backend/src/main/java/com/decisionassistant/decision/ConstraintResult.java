package com.decisionassistant.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

import static com.decisionassistant.decision.DecisionTypes.ConstraintStatus;

@Entity
@Table(name = "constraint_results")
public class ConstraintResult {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private Decision decision;

    @Column(name = "option_id", nullable = false)
    private UUID optionId;

    @Column(name = "constraint_id", nullable = false)
    private UUID constraintId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConstraintStatus status;

    @Column(columnDefinition = "text")
    private String note;

    protected ConstraintResult() {
    }

    ConstraintResult(UUID id, Decision decision, UUID optionId, UUID constraintId,
                     ConstraintStatus status, String note) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.decision = decision;
        update(optionId, constraintId, status, note);
    }

    void update(UUID optionId, UUID constraintId, ConstraintStatus status, String note) {
        this.optionId = optionId;
        this.constraintId = constraintId;
        this.status = status;
        this.note = note;
    }

    public UUID id() { return id; }
    public UUID optionId() { return optionId; }
    public UUID constraintId() { return constraintId; }
    public ConstraintStatus status() { return status; }
    public String note() { return note; }
}
