package com.decisionassistant.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "structure_runs")
public class StructureRun {

    public enum Status { PENDING, SUCCEEDED, FAILED }

    @Id
    private UUID id;
    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;
    @Column(name = "decision_version", nullable = false)
    private long decisionVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
    private AiContracts.StructureInput inputSnapshot;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private AiContracts.StructureProposal result;
    @Column(columnDefinition = "text")
    private String error;
    @Column(length = 100)
    private String provider;
    @Column(length = 200)
    private String model;
    @Column(name = "prompt_version", length = 50)
    private String promptVersion;
    @Column(name = "schema_version", length = 50)
    private String schemaVersion;
    @Column(name = "latency_ms")
    private Long latencyMs;
    @Column(name = "input_tokens")
    private Long inputTokens;
    @Column(name = "output_tokens")
    private Long outputTokens;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StructureRun() {
    }

    public StructureRun(UUID decisionId, long decisionVersion, AiContracts.StructureInput inputSnapshot, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.decisionId = decisionId;
        this.decisionVersion = decisionVersion;
        this.inputSnapshot = inputSnapshot;
        this.status = Status.PENDING;
        this.createdAt = createdAt;
    }

    public void succeeded(AiContracts.StructureProposal result, String provider, String model, String promptVersion,
                          String schemaVersion, long latencyMs, long inputTokens, long outputTokens) {
        this.status = Status.SUCCEEDED;
        this.result = result;
        this.error = null;
        metadata(provider, model, promptVersion, schemaVersion, latencyMs);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public void failed(String error, String provider, String model, String promptVersion,
                       String schemaVersion, long latencyMs) {
        this.status = Status.FAILED;
        this.error = error;
        metadata(provider, model, promptVersion, schemaVersion, latencyMs);
    }

    private void metadata(String provider, String model, String promptVersion, String schemaVersion, long latencyMs) {
        this.provider = provider;
        this.model = model;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.latencyMs = latencyMs;
    }

    public UUID id() { return id; }
    public UUID decisionId() { return decisionId; }
    public long decisionVersion() { return decisionVersion; }
    public AiContracts.StructureInput inputSnapshot() { return inputSnapshot; }
    public Status status() { return status; }
    public AiContracts.StructureProposal result() { return result; }
    public String error() { return error; }
    public String provider() { return provider; }
    public String model() { return model; }
    public String promptVersion() { return promptVersion; }
    public String schemaVersion() { return schemaVersion; }
    public Long latencyMs() { return latencyMs; }
    public Long inputTokens() { return inputTokens; }
    public Long outputTokens() { return outputTokens; }
    public Instant createdAt() { return createdAt; }
}
