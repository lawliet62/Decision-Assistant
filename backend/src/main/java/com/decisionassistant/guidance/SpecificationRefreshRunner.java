package com.decisionassistant.guidance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import static com.decisionassistant.guidance.DiscoveryContracts.ProductFact;

/** 승인된 상품별 검증만 실행하며 시도 간격과 revision 경계를 공통으로 적용한다. */
final class SpecificationRefreshRunner {
    @FunctionalInterface interface Source { String fetch() throws Exception; }
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SpecificationRefreshRunner.class);
    private final boolean enabled;
    private final CatalogProperties config;
    private final CatalogProductRepository products;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String id;
    private final Source source;
    private final Predicate<ProductFact> supports;
    private final BiPredicate<String, ProductFact> matches;

    SpecificationRefreshRunner(boolean enabled, CatalogProperties config, CatalogProductRepository products,
            JdbcTemplate jdbc, PlatformTransactionManager manager, String id, Source source,
            Predicate<ProductFact> supports, BiPredicate<String, ProductFact> matches) {
        this.enabled = enabled; this.config = config; this.products = products; this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager); this.id = id; this.source = source;
        this.supports = supports; this.matches = matches;
    }

    void refresh() {
        if (!enabled || !config.enabled()) return;
        var now = Instant.now();
        var revision = tx.execute(status -> {
            var product = products.findById(id).orElse(null);
            if (product == null || products.findByActiveTrueOrderByIdAsc().stream().noneMatch(p -> id.equals(p.id()))) return null;
            if (product.facts().stream().noneMatch(f -> supports.test(f.evidence()))) return null;
            var updated = jdbc.update("""
                    insert into catalog_spec_attempts(product_id,next_attempt_at) values (?,?)
                    on conflict(product_id) do update set next_attempt_at=excluded.next_attempt_at
                    where catalog_spec_attempts.next_attempt_at <= ?
                    """, id, java.sql.Timestamp.from(now.plus(Duration.ofDays(7))), java.sql.Timestamp.from(now));
            return updated == 1 ? product.revision() : null;
        });
        if (revision == null) return;
        final String html;
        try { html = source.fetch(); }
        catch (Exception e) { log.warn("catalog_spec_refresh product_id={} outcome=SOURCE_UNAVAILABLE", id); return; }
        tx.executeWithoutResult(status -> {
            var product = products.findById(id).orElse(null);
            if (product == null || product.revision() != revision) return;
            for (var fact : product.facts()) {
                var evidence = fact.evidence();
                if (!supports.test(evidence)) continue;
                if (matches.test(html, evidence)) {
                    product.reviewedFact(revision, evidence.name(), now);
                    log.info("catalog_spec_refresh product_id={} field={} outcome=VERIFIED", id, evidence.name());
                } else log.warn("catalog_spec_refresh product_id={} field={} outcome=UNVERIFIED", id, evidence.name());
            }
            products.save(product);
        });
    }
}
