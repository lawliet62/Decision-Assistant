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
    @Column(name = "schema_version", nullable = false, length = 50) private String schemaVersion = "discovery-v1";
    protected ProductDiscovery() {}
    public ProductDiscovery(UUID decisionId, DiscoveryContracts.DiscoveryRequest conditions) {
        this.decisionId = decisionId; this.conditions = conditions; pending();
    }
    public void pending() { status = Status.PENDING; error = null; updatedAt = Instant.now(); }
    public void running() { status = Status.RUNNING; updatedAt = Instant.now(); }
    public void finish(Status status, String error) { this.status = status; this.error = error; updatedAt = Instant.now(); }
    public void usage(String provider, String model, long input, long output, long latency) {
        promptVersion = DiscoveryContracts.PROMPT_VERSION;
        this.provider = provider; this.model = model; inputTokens = input; outputTokens = output; latencyMs = latency;
    }
    public UUID decisionId() { return decisionId; }
    public long version() { return version; }
    public DiscoveryContracts.DiscoveryRequest conditions() { return conditions; }
    public Status status() { return status; }
    public String error() { return error; }
    public Instant updatedAt() { return updatedAt; }
}
