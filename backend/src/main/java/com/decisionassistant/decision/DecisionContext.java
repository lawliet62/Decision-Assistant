package com.decisionassistant.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class DecisionContext {

    @Column(name = "context_intended_use", length = 1000)
    private String intendedUse;

    @Column(name = "context_budget", precision = 19, scale = 4)
    private BigDecimal budget;

    @Column(name = "context_currency", length = 3)
    private String currency;

    @Column(name = "context_notes", columnDefinition = "text")
    private String notes;

    protected DecisionContext() {
    }

    public DecisionContext(String intendedUse, BigDecimal budget, String currency, String notes) {
        this.intendedUse = intendedUse;
        this.budget = budget;
        this.currency = currency;
        this.notes = notes;
    }

    public String intendedUse() { return intendedUse; }
    public BigDecimal budget() { return budget; }
    public String currency() { return currency; }
    public String notes() { return notes; }
}
