package com.decisionassistant.preference;

import com.decisionassistant.decision.DecisionTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "preference_memories")
public class PreferenceMemory {
    public enum Status { PROPOSED, CONFIRMED, DISMISSED }
    public enum Confidence { LOW, MEDIUM, HIGH }

    @Id private UUID id;
    @Column(name = "owner_user_id", nullable = false) private UUID ownerUserId;
    @Enumerated(EnumType.STRING) @Column(name = "template_key", length = 30) private DecisionTypes.TemplateKey templateKey;
    @Column(name = "criterion_name", length = 200) private String criterionName;
    @Column(nullable = false, columnDefinition = "text") private String statement;
    @Column(nullable = false) private int strength;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Confidence confidence;
    @Column(name = "source_decision_id") private UUID sourceDecisionId;
    @Column(name = "source_outcome_id") private UUID sourceOutcomeId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PreferenceMemory() {}
    public PreferenceMemory(UUID ownerUserId, DecisionTypes.TemplateKey templateKey, String criterionName,
                            String statement, int strength, UUID sourceDecisionId, UUID sourceOutcomeId, Instant now) {
        this.id = UUID.randomUUID(); this.ownerUserId = ownerUserId; this.templateKey = templateKey;
        this.criterionName = criterionName; this.statement = statement; this.strength = strength;
        this.status = Status.PROPOSED; this.confidence = Confidence.LOW;
        this.sourceDecisionId = sourceDecisionId; this.sourceOutcomeId = sourceOutcomeId;
        this.createdAt = now; this.updatedAt = now;
    }
    public void confirm(Instant now) { status = Status.CONFIRMED; updatedAt = now; }
    public void dismiss(Instant now) { status = Status.DISMISSED; updatedAt = now; }
    public void update(String statement, int strength, Instant now) {
        this.statement = statement; this.strength = strength; status = Status.CONFIRMED; updatedAt = now;
    }
    public UUID id() { return id; }
    public UUID ownerUserId() { return ownerUserId; }
    public DecisionTypes.TemplateKey templateKey() { return templateKey; }
    public String criterionName() { return criterionName; }
    public String statement() { return statement; }
    public int strength() { return strength; }
    public Status status() { return status; }
    public Confidence confidence() { return confidence; }
    public UUID sourceDecisionId() { return sourceDecisionId; }
    public UUID sourceOutcomeId() { return sourceOutcomeId; }
}
