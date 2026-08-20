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

import static com.decisionassistant.decision.DecisionTypes.CriterionKind;
import static com.decisionassistant.decision.DecisionTypes.PreferenceDirection;

@Entity
@Table(name = "criteria")
public class Criterion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private Decision decision;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CriterionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PreferenceDirection direction;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal weight;

    @Column(name = "worst_anchor", precision = 19, scale = 4)
    private BigDecimal worstAnchor;

    @Column(name = "best_anchor", precision = 19, scale = 4)
    private BigDecimal bestAnchor;

    @Column(length = 30)
    private String unit;

    @Column(nullable = false)
    private boolean active;

    protected Criterion() {
    }

    Criterion(UUID id, Decision decision, String name, CriterionKind kind, PreferenceDirection direction,
              BigDecimal weight, BigDecimal worstAnchor, BigDecimal bestAnchor, String unit, boolean active) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.decision = decision;
        update(name, kind, direction, weight, worstAnchor, bestAnchor, unit, active);
    }

    void update(String name, CriterionKind kind, PreferenceDirection direction, BigDecimal weight,
                BigDecimal worstAnchor, BigDecimal bestAnchor, String unit, boolean active) {
        this.name = name;
        this.kind = kind;
        this.direction = direction;
        this.weight = weight;
        this.worstAnchor = worstAnchor;
        this.bestAnchor = bestAnchor;
        this.unit = unit;
        this.active = active;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public CriterionKind kind() { return kind; }
    public PreferenceDirection direction() { return direction; }
    public BigDecimal weight() { return weight; }
    public BigDecimal worstAnchor() { return worstAnchor; }
    public BigDecimal bestAnchor() { return bestAnchor; }
    public String unit() { return unit; }
    public boolean active() { return active; }
}
