package com.decisionassistant.decision;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "memory_insights")
public class MemoryInsight {
    @Id UUID id;
    @Column(name = "decision_id", nullable = false) UUID decisionId;
    @Column(name = "source_decision_id", nullable = false) UUID sourceDecisionId;
    @Column(name = "resolution_id", nullable = false) UUID resolutionId;
    @Column(name = "input_id") UUID inputId;
    @Column(name = "input_hash", nullable = false) String inputHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") MemoryInsightContracts.Insight result;
    @Column(nullable = false) String provider;
    @Column(nullable = false) String model;
    @Column(name = "prompt_version", nullable = false) String promptVersion;
    @Column(name = "schema_version", nullable = false) String schemaVersion;
    @Column(name = "input_tokens", nullable = false) long inputTokens;
    @Column(name = "output_tokens", nullable = false) long outputTokens;
    @Column(name = "latency_ms", nullable = false) long latencyMs;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    protected MemoryInsight() {}
    MemoryInsight(UUID decisionId, UUID source, UUID resolution, UUID inputId, String hash, MemoryInsightContracts.Insight result,
                  String provider, String model, long inputTokens, long outputTokens, long latencyMs) {
        id = UUID.randomUUID(); this.decisionId = decisionId; sourceDecisionId = source; resolutionId = resolution;
        this.inputId = inputId;
        inputHash = hash; this.result = result; this.provider = provider; this.model = model;
        this.inputTokens = inputTokens; this.outputTokens = outputTokens; this.latencyMs = latencyMs;
        promptVersion = MemoryInsightContracts.PROMPT_VERSION; schemaVersion = "memory-insight-v1"; createdAt = Instant.now();
    }
}
