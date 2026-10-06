package com.decisionassistant.guidance;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_discoveries")
public class ProductDiscovery {
    public enum Status { PENDING, RUNNING, SUCCEEDED, PARTIAL, FAILED }
    @Id @Column(name = "decision_id") private UUID decisionId;
    @Version private long version;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private DiscoveryContracts.DiscoveryRequest conditions;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(columnDefinition = "text") private String error;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(length = 100) private String provider;
    @Column(length = 200) private String model;
    @Column(name = "input_tokens") private Long inputTokens;
    @Column(name = "output_tokens") private Long outputTokens;
    @Column(name = "latency_ms") private Long latencyMs;
    @Column(name = "prompt_version", nullable = false, length = 50) private String promptVersion = DiscoveryContracts.PROMPT_VERSION;
    @Column(name = "schema_version", nullable = false, length = 50) private String schemaVersion = "discovery-v2";
    @Column(name = "execution_id") private UUID executionId;
    @Column(length = 30) private String stage;
    @Column(name = "policy_version", length = 50) private String policyVersion;
    @Column(name = "empty_reason", length = 50) private String emptyReason;
    @Column(nullable = false) private int generation;
    @Column(name = "displayed_generation", nullable = false) private int displayedGeneration;
    public record Round(int generation, String status, String error, String emptyReason, String policyVersion, Instant completedAt) {}
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private java.util.List<Round> history = java.util.List.of();
    protected ProductDiscovery() {}
    public ProductDiscovery(UUID decisionId, DiscoveryContracts.DiscoveryRequest conditions) {
        this.decisionId = decisionId; this.conditions = conditions; pending();
    }
    public void pending() { status = Status.PENDING; error = null; emptyReason = null; stage = "QUEUED"; executionId = UUID.randomUUID(); policyVersion = OfficialStorePolicy.VERSION; updatedAt = Instant.now(); }
    public void running() { status = Status.RUNNING; stage = "CONFIGURATION"; if (executionId == null) executionId = UUID.randomUUID(); policyVersion = OfficialStorePolicy.VERSION; updatedAt = Instant.now(); }
    public void stage(DiscoveryFailure.Stage stage) { this.stage = stage.name(); updatedAt = Instant.now(); }
    public void finish(Status status, String error) { this.status = status; this.error = error; stage = null; updatedAt = Instant.now(); remember(); }
    public void empty(String reason) { emptyReason = reason; finish(Status.SUCCEEDED, null); }
    public void refresh() { remember(); generation++; pending(); }
    public void displayCurrentGeneration() { displayedGeneration = generation; }
    public void catalog() { policyVersion = CatalogProperties.VERSION; promptVersion = "none"; schemaVersion = CatalogProperties.VERSION; }
    private void remember() {
        if (status == Status.PENDING || status == Status.RUNNING) return;
        var records = new java.util.ArrayList<>(history.stream().filter(item -> item.generation() != generation).toList());
        records.add(new Round(generation, status.name(), error, emptyReason, policyVersion, updatedAt));
        history = java.util.List.copyOf(records);
    }
    public void usage(String provider, String model, long input, long output, long latency) {
        promptVersion = DiscoveryContracts.PROMPT_VERSION;
        schemaVersion = "discovery-v2";
        this.provider = provider; this.model = model; inputTokens = input; outputTokens = output; latencyMs = latency;
    }
    public UUID decisionId() { return decisionId; }
    public long version() { return version; }
    public DiscoveryContracts.DiscoveryRequest conditions() { return conditions; }
    public Status status() { return status; }
    public String error() { return error; }
    public Instant updatedAt() { return updatedAt; }
    public UUID executionId() { return executionId; }
    public String stage() { return stage; }
    public String policyVersion() { return policyVersion; }
    public String emptyReason() { return emptyReason; }
    public int generation() { return generation; }
    public int displayedGeneration() { return displayedGeneration; }
    public java.util.List<Round> history() { return history; }
}
