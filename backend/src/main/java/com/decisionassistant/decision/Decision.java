package com.decisionassistant.decision;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.decisionassistant.decision.DecisionTypes.DecisionStatus;
import static com.decisionassistant.decision.DecisionTypes.ProcessingStatus;
import static com.decisionassistant.decision.DecisionTypes.TemplateKey;

@Entity
@Table(name = "decisions")
public class Decision {
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "revision_draft", columnDefinition = "jsonb")
    private DecisionDtos.DecisionResponse revisionDraft;
    public DecisionDtos.DecisionResponse revisionDraft() { return revisionDraft; }
    public void revisionDraft(DecisionDtos.DecisionResponse snapshot) { revisionDraft = snapshot; }
    public void cancelRevision(Instant now) {
        status = revisionDraft.status(); currentResolutionId = revisionBaseId;
        editMode = null; revisionBaseId = null; revisionDraft = null; updatedAt = now;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "edit_mode", length = 30)
    private DecisionTypes.EditMode editMode;
    @Column(name = "current_resolution_id")
    private UUID currentResolutionId;
    @Column(name = "revision_base_id")
    private UUID revisionBaseId;

    public DecisionTypes.EditMode editMode() { return editMode; }
    public UUID currentResolutionId() { return currentResolutionId; }
    public UUID revisionBaseId() { return revisionBaseId; }
    public void beginRevision(DecisionTypes.EditMode mode, UUID baseId, Instant now) {
        editMode = mode; revisionBaseId = baseId; currentResolutionId = null;
        status = DecisionStatus.DRAFT; archivedAt = null; updatedAt = now;
    }
    public void recordResolution(UUID id, boolean completed) {
        currentResolutionId = id;
        if (completed) { editMode = null; revisionBaseId = null; revisionDraft = null; }
    }

    @Column(name = "archived_at")
    private Instant archivedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Column(name = "personalization_enabled", nullable = false)
    private boolean personalizationEnabled = true;

    public Instant archivedAt() { return archivedAt; }
    public Instant deletedAt() { return deletedAt; }
    public boolean personalizationEnabled() { return personalizationEnabled; }
    public void archive(boolean archived, Instant now) { archivedAt = archived ? now : null; updatedAt = now; }
    public void trash(Instant now) { deletedAt = now; updatedAt = now; }
    public void restore(Instant now) { deletedAt = null; updatedAt = now; }
    public void personalization(boolean enabled, Instant now) { personalizationEnabled = enabled; updatedAt = now; }

    @Id
    private UUID id;

    @Version
    private long version;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_key", nullable = false, length = 30)
    private TemplateKey templateKey;

    @Column(name = "template_version", nullable = false, length = 50)
    private String templateVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DecisionStatus status;

    @Column(name = "intent_text", columnDefinition = "text")
    private String intentText;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 30)
    private ProcessingStatus processingStatus;

    @Column(name = "ai_consent", nullable = false)
    private boolean aiConsent;

    @Column(name = "web_consent", nullable = false)
    private boolean webConsent;

    @Column(name = "consent_policy_version", length = 50)
    private String consentPolicyVersion;

    @Embedded
    private DecisionContext context;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "comparison_state", columnDefinition = "jsonb")
    private Comparison.State comparisonState;

    public Comparison.State comparisonState() { return comparisonState; }
    public void comparisonState(Comparison.State state, Instant now) {
        this.comparisonState = state;
        this.updatedAt = now;
    }

    @OneToMany(mappedBy = "decision", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name asc")
    private final List<DecisionOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "decision", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name asc")
    private final List<Criterion> criteria = new ArrayList<>();

    @OneToMany(mappedBy = "decision", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OptionValue> values = new ArrayList<>();

    @OneToMany(mappedBy = "decision", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name asc")
    private final List<DecisionConstraint> constraints = new ArrayList<>();

    @OneToMany(mappedBy = "decision", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ConstraintResult> constraintResults = new ArrayList<>();

    protected Decision() {
    }

    public Decision(UUID ownerUserId, String title, TemplateKey templateKey, String templateVersion, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerUserId = ownerUserId;
        this.title = title;
        this.templateKey = templateKey;
        this.templateVersion = templateVersion;
        this.status = DecisionStatus.DRAFT;
        this.processingStatus = ProcessingStatus.READY;
        this.context = new DecisionContext(null, null, null, null);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDraft(String title, DecisionContext context, Instant now) {
        this.title = title;
        this.context = context;
        this.updatedAt = now;
    }

    public void startGuidedIntake(String intentText, boolean aiConsent, boolean webConsent,
                                  String policyVersion, Instant now) {
        this.intentText = intentText;
        this.aiConsent = aiConsent;
        this.webConsent = webConsent;
        this.consentPolicyVersion = policyVersion;
        this.processingStatus = webConsent ? ProcessingStatus.IMPORTING : ProcessingStatus.NEEDS_INPUT;
        this.updatedAt = now;
    }

    public void processingStatus(ProcessingStatus processingStatus, Instant now) {
        this.processingStatus = processingStatus;
        this.updatedAt = now;
    }

    public void applyImportedOption(UUID optionId, String name, String sourceText, Instant now) {
        options.stream().filter(option -> option.id().equals(optionId)).findFirst()
                .ifPresent(option -> option.applyImportedSource(name, sourceText));
        this.updatedAt = now;
    }

    public void upsertGeneratedValue(UUID optionId, UUID criterionId, java.math.BigDecimal numericValue,
                                     Integer rating, DecisionTypes.ValueCertainty certainty,
                                     String sourceUrl, String sourceNote, DecisionTypes.ValueOrigin origin) {
        var existing = values.stream().filter(value -> value.optionId().equals(optionId)
                && value.criterionId().equals(criterionId)).findFirst();
        if (existing.isPresent()) {
            var value = existing.get();
            if (value.origin() == DecisionTypes.ValueOrigin.USER
                    || value.verificationStatus() == DecisionTypes.VerificationStatus.CONFIRMED) return;
            value.update(optionId, criterionId, numericValue, rating, certainty, sourceUrl, sourceNote);
            value.markGenerated(origin, DecisionTypes.VerificationStatus.UNREVIEWED);
            return;
        }
        var value = addValue(null, optionId, criterionId, numericValue, rating, certainty, sourceUrl, sourceNote);
        value.markGenerated(origin, DecisionTypes.VerificationStatus.UNREVIEWED);
    }

    public boolean confirmValue(UUID valueId, java.math.BigDecimal numericValue, Integer rating, boolean disputed,
                                Instant now) {
        var value = values.stream().filter(item -> item.id().equals(valueId)).findFirst().orElse(null);
        if (value == null) return false;
        value.update(value.optionId(), value.criterionId(), numericValue == null ? value.numericValue() : numericValue,
                rating == null ? value.rating() : rating, DecisionTypes.ValueCertainty.CONFIRMED,
                value.sourceUrl(), value.sourceNote());
        value.markGenerated(value.origin(), disputed ? DecisionTypes.VerificationStatus.DISPUTED
                : DecisionTypes.VerificationStatus.CONFIRMED);
        this.updatedAt = now;
        return true;
    }

    public DecisionOption addOption(UUID id, String name, String description, String sourceUrl, String sourceText) {
        var item = new DecisionOption(id, this, name, description, sourceUrl, sourceText);
        options.add(item);
        return item;
    }

    public Criterion addCriterion(UUID id, String name, DecisionTypes.CriterionKind kind,
                                  DecisionTypes.PreferenceDirection direction, java.math.BigDecimal weight,
                                  java.math.BigDecimal worstAnchor, java.math.BigDecimal bestAnchor,
                                  String unit, boolean active) {
        var item = new Criterion(id, this, name, kind, direction, weight, worstAnchor, bestAnchor, unit, active);
        criteria.add(item);
        return item;
    }

    public OptionValue addValue(UUID id, UUID optionId, UUID criterionId, java.math.BigDecimal numericValue,
                                Integer rating, DecisionTypes.ValueCertainty certainty,
                                String sourceUrl, String sourceNote) {
        var item = new OptionValue(id, this, optionId, criterionId, numericValue, rating, certainty, sourceUrl, sourceNote);
        values.add(item);
        return item;
    }

    public DecisionConstraint addConstraint(UUID id, String name, DecisionTypes.ConstraintKind kind,
                                            UUID criterionId, java.math.BigDecimal threshold, String unit,
                                            String description, boolean active) {
        var item = new DecisionConstraint(id, this, name, kind, criterionId, threshold, unit, description, active);
        constraints.add(item);
        return item;
    }

    public ConstraintResult addConstraintResult(UUID id, UUID optionId, UUID constraintId,
                                                DecisionTypes.ConstraintStatus status, String note) {
        var item = new ConstraintResult(id, this, optionId, constraintId, status, note);
        constraintResults.add(item);
        return item;
    }

    public void clearDraftItems() {
        values.clear();
        constraintResults.clear();
        constraints.clear();
        criteria.clear();
        options.clear();
    }

    public void resolve(DecisionStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public UUID id() { return id; }
    public UUID ownerUserId() { return ownerUserId; }
    public long version() { return version; }
    public String title() { return title; }
    public TemplateKey templateKey() { return templateKey; }
    public String templateVersion() { return templateVersion; }
    public DecisionStatus status() { return status; }
    public String intentText() { return intentText; }
    public ProcessingStatus processingStatus() { return processingStatus; }
    public boolean aiConsent() { return aiConsent; }
    public boolean webConsent() { return webConsent; }
    public String consentPolicyVersion() { return consentPolicyVersion; }
    public DecisionContext context() {
        return context == null ? new DecisionContext(null, null, null, null) : context;
    }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<DecisionOption> options() { return List.copyOf(options); }
    public List<Criterion> criteria() { return List.copyOf(criteria); }
    public List<OptionValue> values() { return List.copyOf(values); }
    public List<DecisionConstraint> constraints() { return List.copyOf(constraints); }
    public List<ConstraintResult> constraintResults() { return List.copyOf(constraintResults); }

    List<DecisionOption> mutableOptions() { return options; }
    List<Criterion> mutableCriteria() { return criteria; }
    List<OptionValue> mutableValues() { return values; }
    List<DecisionConstraint> mutableConstraints() { return constraints; }
    List<ConstraintResult> mutableConstraintResults() { return constraintResults; }
}
