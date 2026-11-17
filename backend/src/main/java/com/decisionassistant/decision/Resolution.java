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
    public enum PurchaseStatus { UNRECORDED, PLANNED, PURCHASED, NOT_PURCHASED }
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "reason_ids", nullable = false, columnDefinition = "jsonb")
    private java.util.List<String> reasonIds = java.util.List.of();
    @Column(name = "primary_reason_id")
    private String primaryReasonId;
    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_status", nullable = false)
    private PurchaseStatus purchaseStatus = PurchaseStatus.UNRECORDED;
    @Column(name = "purchase_recorded_at")
    private Instant purchaseRecordedAt;
    public java.util.List<String> reasonIds() { return reasonIds; }
    public String primaryReasonId() { return primaryReasonId; }
    public PurchaseStatus purchaseStatus() { return purchaseStatus; }
    public Instant purchaseRecordedAt() { return purchaseRecordedAt; }
    public void reasons(java.util.List<String> ids, String primary) { reasonIds = ids; primaryReasonId = primary; }
    public void purchase(PurchaseStatus status, Instant now) { purchaseStatus = status; purchaseRecordedAt = now; }

    @Enumerated(EnumType.STRING)
    @Column(name = "edit_mode", length = 30)
    private DecisionTypes.EditMode editMode;
    @Column(name = "previous_resolution_id")
    private UUID previousResolutionId;
    @Column(name = "superseded_at")
    private Instant supersededAt;

    public DecisionTypes.EditMode editMode() { return editMode; }
    public UUID previousResolutionId() { return previousResolutionId; }
    public Instant supersededAt() { return supersededAt; }
    public void revision(DecisionTypes.EditMode mode, UUID previous) { editMode = mode; previousResolutionId = previous; }
    public void supersede(Instant now) { supersededAt = now; }

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

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "comparison_snapshot", columnDefinition = "jsonb")
    private Comparison.Snapshot comparisonSnapshot;

    public Comparison.Snapshot comparisonSnapshot() { return comparisonSnapshot; }

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

    public Resolution(UUID decisionId, ResolutionKind kind, UUID selectedOptionId, String rationale,
                      Instant createdAt, Comparison.Snapshot snapshot) {
        this(decisionId, kind, selectedOptionId, rationale, createdAt);
        this.comparisonSnapshot = snapshot;
    }

    public UUID id() { return id; }
    public UUID decisionId() { return decisionId; }
    public ResolutionKind kind() { return kind; }
    public UUID selectedOptionId() { return selectedOptionId; }
    public String rationale() { return rationale; }
    public Instant createdAt() { return createdAt; }
}
