package com.decisionassistant.evaluation;

import com.decisionassistant.ai.AiContracts;
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
@Table(name = "evaluations")
public class Evaluation {

    public enum LlmStatus { NOT_REQUESTED, PENDING, SUCCEEDED, FAILED }

    @Id
    private UUID id;
    @Column(name = "decision_id", nullable = false)
    private UUID decisionId;
    @Column(name = "decision_version", nullable = false)
    private long decisionVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
    private DecisionSnapshot inputSnapshot;
    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "algorithm_result", nullable = false, columnDefinition = "jsonb")
    private AlgorithmResult algorithmResult;
    @Enumerated(EnumType.STRING)
    @Column(name = "llm_status", nullable = false, length = 30)
    private LlmStatus llmStatus;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_result", columnDefinition = "jsonb")
    private AiContracts.LlmAssessment llmResult;
    @Column(name = "llm_error", columnDefinition = "text")
    private String llmError;
    @Column(length = 100)
    private String provider;
    @Column(length = 200)
    private String model;
    @Column(name = "prompt_version", length = 50)
    private String promptVersion;
    @Column(name = "schema_version", length = 50)
    private String schemaVersion;
    @Column(name = "llm_latency_ms")
    private Long llmLatencyMs;
    @Column(name = "input_tokens")
    private Long inputTokens;
    @Column(name = "output_tokens")
    private Long outputTokens;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Evaluation() {
    }

    public Evaluation(UUID decisionId, long decisionVersion, DecisionSnapshot inputSnapshot, String inputHash,
                      AlgorithmResult algorithmResult, boolean includeLlm, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.decisionId = decisionId;
        this.decisionVersion = decisionVersion;
        this.inputSnapshot = inputSnapshot;
        this.inputHash = inputHash;
        this.algorithmResult = algorithmResult;
        this.llmStatus = includeLlm ? LlmStatus.PENDING : LlmStatus.NOT_REQUESTED;
        this.createdAt = createdAt;
    }

    public void llmSucceeded(AiContracts.LlmAssessment result, String provider, String model, String promptVersion,
                             String schemaVersion, long latencyMs, long inputTokens, long outputTokens) {
        this.llmStatus = LlmStatus.SUCCEEDED;
        this.llmResult = result;
        this.llmError = null;
        this.provider = provider;
        this.model = model;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.llmLatencyMs = latencyMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public void llmPending() {
        this.llmStatus = LlmStatus.PENDING;
        this.llmResult = null;
        this.llmError = null;
    }

    public void llmFailed(String error, String provider, String model, String promptVersion,
                          String schemaVersion, long latencyMs) {
        this.llmStatus = LlmStatus.FAILED;
        this.llmError = error;
        this.provider = provider;
        this.model = model;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.llmLatencyMs = latencyMs;
    }

    public UUID id() { return id; }
    public UUID decisionId() { return decisionId; }
    public long decisionVersion() { return decisionVersion; }
    public DecisionSnapshot inputSnapshot() { return inputSnapshot; }
    public String inputHash() { return inputHash; }
    public AlgorithmResult algorithmResult() { return algorithmResult; }
    public LlmStatus llmStatus() { return llmStatus; }
    public AiContracts.LlmAssessment llmResult() { return llmResult; }
    public String llmError() { return llmError; }
    public String provider() { return provider; }
    public String model() { return model; }
    public String promptVersion() { return promptVersion; }
    public String schemaVersion() { return schemaVersion; }
    public Long llmLatencyMs() { return llmLatencyMs; }
    public Long inputTokens() { return inputTokens; }
    public Long outputTokens() { return outputTokens; }
    public Instant createdAt() { return createdAt; }
}
