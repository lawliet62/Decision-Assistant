package com.decisionassistant.guidance;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

@Entity
@Table(name = "catalog_products")
public class CatalogProduct {
    public enum ReviewCycle { HARDWARE, SOFTWARE }
    public record Fact(ProductFact evidence, ReviewCycle cycle) {
        public Instant reviewDueAt() { return evidence.fetchedAt().plus(Duration.ofDays(cycle == ReviewCycle.HARDWARE ? 90 : 30)); }
    }
    @Id @Column(length = 100) private String id;
    private int revision;
    @Column(nullable = false, length = 300) private String name;
    @Column(name = "purchase_url", nullable = false, length = 2048) private String purchaseUrl;
    @Column(name = "expected_identity", nullable = false, length = 500) private String expectedIdentity;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private List<Fact> facts;
    private boolean active;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private OfficialOffer offer;
    @Column(name = "price_quote") private String priceQuote;
    @Column(name = "price_status", nullable = false, length = 40) private String priceStatus;
    @Column(name = "last_attempt_at") private Instant lastAttemptAt;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Version private long version;
    protected CatalogProduct() {}
    public CatalogProduct(String id) { this.id = id; priceStatus = "UNVERIFIED"; nextAttemptAt = Instant.EPOCH; facts = List.of(); }
    public void register(int revision, String name, String url, String identity, List<Fact> facts, boolean active) {
        if (this.revision > revision) return;
        if (this.revision == revision) {
            if (!Objects.equals(this.name, name) || !Objects.equals(purchaseUrl, url)
                    || !Objects.equals(expectedIdentity, identity) || !Objects.equals(this.facts, facts) || this.active != active)
                throw new IllegalArgumentException("카탈로그 수정 시 revision을 올려야 합니다.");
            return;
        }
        if (!Objects.equals(purchaseUrl, url) || !Objects.equals(expectedIdentity, identity)) {
            offer = null; priceQuote = null; priceStatus = "UNVERIFIED"; nextAttemptAt = Instant.EPOCH;
        }
        this.revision = revision; this.name = name; purchaseUrl = url; expectedIdentity = identity;
        this.facts = List.copyOf(facts); this.active = active;
    }
    public void attempted(Instant now) { lastAttemptAt = now; nextAttemptAt = now.plus(Duration.ofDays(1)); }
    public void observed(OfficialProduct product) {
        var source = product.offer();
        offer = new OfficialOffer(source.purchaseUrl(), source.seller(), source.price(), source.currency(), source.imageUrl(),
                source.fetchedAt(), source.priceBasis(), CatalogProperties.VERSION);
        priceStatus = "VERIFIED";
        priceQuote = product.priceQuote();
    }
    public void rejected(String outcome) {
        // 통신 실패는 마지막 관측을 보존하되, 상품/가격 검증 실패는 즉시 신규 추천에서 제외한다.
        if (!"COLLECTION_FAILED".equals(outcome)) priceStatus = outcome;
    }
    public List<ProductFact> usableFacts(Instant now) {
        return facts.stream().filter(f -> f.cycle() == ReviewCycle.HARDWARE || now.isBefore(f.reviewDueAt()))
                .map(Fact::evidence).toList();
    }
    public OfficialOffer usableOffer() { return "VERIFIED".equals(priceStatus) ? offer : null; }
    public String priceQuote() { return priceQuote; }
    public String id() { return id; }
    public String name() { return name; }
    public String purchaseUrl() { return purchaseUrl; }
    public String expectedIdentity() { return expectedIdentity; }
    public int revision() { return revision; }
    public long version() { return version; }
    public List<Fact> facts() { return facts; }
}
