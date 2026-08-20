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

import java.math.BigDecimal;
import java.util.UUID;

import static com.decisionassistant.decision.DecisionTypes.ConstraintKind;

@Entity
@Table(name = "decision_constraints")
public class DecisionConstraint {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private Decision decision;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConstraintKind kind;

    @Column(name = "criterion_id")
    private UUID criterionId;

    @Column(precision = 19, scale = 4)
    private BigDecimal threshold;

    @Column(length = 30)
    private String unit;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean active;

    protected DecisionConstraint() {
    }

    DecisionConstraint(UUID id, Decision decision, String name, ConstraintKind kind, UUID criterionId,
                       BigDecimal threshold, String unit, String description, boolean active) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.decision = decision;
        update(name, kind, criterionId, threshold, unit, description, active);
    }

    void update(String name, ConstraintKind kind, UUID criterionId, BigDecimal threshold,
                String unit, String description, boolean active) {
        this.name = name;
        this.kind = kind;
        this.criterionId = criterionId;
        this.threshold = threshold;
        this.unit = unit;
        this.description = description;
        this.active = active;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public ConstraintKind kind() { return kind; }
    public UUID criterionId() { return criterionId; }
    public BigDecimal threshold() { return threshold; }
    public String unit() { return unit; }
    public String description() { return description; }
    public boolean active() { return active; }
}
