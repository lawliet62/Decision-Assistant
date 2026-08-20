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

import static com.decisionassistant.decision.DecisionTypes.ValueCertainty;

@Entity
@Table(name = "option_values")
public class OptionValue {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private Decision decision;

    @Column(name = "option_id", nullable = false)
    private UUID optionId;

    @Column(name = "criterion_id", nullable = false)
    private UUID criterionId;

    @Column(name = "numeric_value", precision = 19, scale = 4)
    private BigDecimal numericValue;

    private Integer rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValueCertainty certainty;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "source_note", columnDefinition = "text")
    private String sourceNote;

    protected OptionValue() {
    }

    OptionValue(UUID id, Decision decision, UUID optionId, UUID criterionId, BigDecimal numericValue,
                Integer rating, ValueCertainty certainty, String sourceUrl, String sourceNote) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.decision = decision;
        update(optionId, criterionId, numericValue, rating, certainty, sourceUrl, sourceNote);
    }

    void update(UUID optionId, UUID criterionId, BigDecimal numericValue, Integer rating,
                ValueCertainty certainty, String sourceUrl, String sourceNote) {
        this.optionId = optionId;
        this.criterionId = criterionId;
        this.numericValue = numericValue;
        this.rating = rating;
        this.certainty = certainty;
        this.sourceUrl = sourceUrl;
        this.sourceNote = sourceNote;
    }

    public UUID id() { return id; }
    public UUID optionId() { return optionId; }
    public UUID criterionId() { return criterionId; }
    public BigDecimal numericValue() { return numericValue; }
    public Integer rating() { return rating; }
    public ValueCertainty certainty() { return certainty; }
    public String sourceUrl() { return sourceUrl; }
    public String sourceNote() { return sourceNote; }
}
