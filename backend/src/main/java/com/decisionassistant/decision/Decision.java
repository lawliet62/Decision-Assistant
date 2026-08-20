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
import static com.decisionassistant.decision.DecisionTypes.TemplateKey;

@Entity
@Table(name = "decisions")
public class Decision {

    @Id
    private UUID id;

    @Version
    private long version;

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

    @Embedded
    private DecisionContext context;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public Decision(String title, TemplateKey templateKey, String templateVersion, Instant now) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.templateKey = templateKey;
        this.templateVersion = templateVersion;
        this.status = DecisionStatus.DRAFT;
        this.context = new DecisionContext(null, null, null, null);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDraft(String title, DecisionContext context, Instant now) {
        this.title = title;
        this.context = context;
        this.updatedAt = now;
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
    public long version() { return version; }
    public String title() { return title; }
    public TemplateKey templateKey() { return templateKey; }
    public String templateVersion() { return templateVersion; }
    public DecisionStatus status() { return status; }
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
