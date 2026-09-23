package com.decisionassistant.guidance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidate_suggestions")
public class CandidateSuggestion {
    public enum Status { PROPOSED, ACCEPTED, DISMISSED }

    @Id private UUID id;
    @Column(name = "decision_id", nullable = false) private UUID decisionId;
    @Column(nullable = false, length = 300) private String name;
    @Column(nullable = false, length = 2048) private String url;
    @Column(nullable = false, columnDefinition = "text") private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(length = 100) private String provider;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private java.util.List<DiscoveryContracts.ProductFact> facts = java.util.List.of();
    @Column(name = "option_id") private UUID optionId;
    @Column(nullable = false) private boolean discovered;
    @Column(name = "source_excerpt", columnDefinition = "text") private String sourceExcerpt;
    @Column(name = "fetched_at") private Instant fetchedAt;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "official_offer", columnDefinition = "jsonb") private DiscoveryContracts.OfficialOffer officialOffer;
    @Column(name = "product_identity", length = 500) private String productIdentity;
    @Column(nullable = false) private int generation;

    protected CandidateSuggestion() {}
    public CandidateSuggestion(UUID decisionId, String name, String url, String reason, String provider, Instant now) {
        this.id = UUID.randomUUID(); this.decisionId = decisionId; this.name = name; this.url = url;
        this.reason = reason; this.provider = provider; this.status = Status.PROPOSED; this.createdAt = now;
    }
    public void accept() { status = Status.ACCEPTED; }
    public void accept(UUID optionId) { accept(); this.optionId = optionId; }
    public void discovered(java.util.List<DiscoveryContracts.ProductFact> facts) { this.facts = java.util.List.copyOf(facts); discovered = true; }
    public java.util.List<DiscoveryContracts.ProductFact> facts() { return facts; }
    public UUID optionId() { return optionId; }
    public boolean discovered() { return discovered; }
    public void sourceEvidence(String excerpt, Instant fetchedAt) { this.sourceExcerpt = excerpt; this.fetchedAt = fetchedAt; }
    public String sourceExcerpt() { return sourceExcerpt; }
    public Instant fetchedAt() { return fetchedAt; }
    public DiscoveryContracts.OfficialOffer officialOffer() { return officialOffer; }
    public String productIdentity() { return productIdentity; }
    public int generation() { return generation; }
    public void generation(int value) { generation = value; }
    public void officialProduct(DiscoveryContracts.OfficialProduct product) { officialOffer = product.offer(); productIdentity = product.identity(); }
    public void refreshDiscovery(CandidateSuggestion source) {
        reason = source.reason; url = source.url; facts = source.facts;
        sourceExcerpt = source.sourceExcerpt; fetchedAt = source.fetchedAt;
        officialOffer = source.officialOffer; productIdentity = source.productIdentity;
    }
    public void dismiss() { status = Status.DISMISSED; }
    public UUID id() { return id; }
    public UUID decisionId() { return decisionId; }
    public String name() { return name; }
    public String url() { return url; }
    public String reason() { return reason; }
    public Status status() { return status; }
    public String provider() { return provider; }
}
