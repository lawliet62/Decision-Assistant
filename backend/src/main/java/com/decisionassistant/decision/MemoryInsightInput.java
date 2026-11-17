package com.decisionassistant.decision;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/** 동의한 시점의 입력을 외부 호출 전에 고정한다. 두 부모의 삭제 수명을 함께 따른다. */
@Entity
@Table(name = "memory_insight_inputs")
public class MemoryInsightInput {
    @Id UUID id;
    @Column(name = "decision_id", nullable = false) UUID decisionId;
    @Column(name = "source_decision_id", nullable = false) UUID sourceDecisionId;
    @Column(name = "resolution_id", nullable = false) UUID resolutionId;
    @Column(name = "input_hash", nullable = false) String inputHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") MemoryInsightContracts.Input snapshot;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    protected MemoryInsightInput() {}
    MemoryInsightInput(UUID decisionId, String hash, MemoryInsightContracts.Input snapshot) {
        id = UUID.randomUUID(); this.decisionId = decisionId; this.inputHash = hash; this.snapshot = snapshot;
        sourceDecisionId = snapshot.source().decisionId(); resolutionId = snapshot.source().resolutionId();
        createdAt = Instant.now();
    }
}
