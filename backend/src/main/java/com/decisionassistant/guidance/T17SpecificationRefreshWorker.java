package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

@Component
public class T17SpecificationRefreshWorker {
    private static final String ID = "qcy-t17-app";
    private static final String URL = "https://qcystore.co.kr/product/detail.html?product_no=563";
    private static final Map<String, List<String>> CHECKS = Map.of(
            "형태", List.of("커널형", "커널형"));
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(T17SpecificationRefreshWorker.class);
    private final boolean enabled;
    private final CatalogProperties config;
    private final CatalogProductRepository products;
    private final JdbcTemplate jdbc;
    private final QcySpecificationSource source;
    private final TransactionTemplate tx;
    public T17SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            QcySpecificationSource source, PlatformTransactionManager manager) {
        this.enabled = enabled; this.config = config; this.products = products; this.jdbc = jdbc; this.source = source;
        tx = new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() {
        if (!enabled || !config.enabled()) return;
        var now = Instant.now();
        var revision = tx.execute(status -> {
            var product = products.findById(ID).orElse(null);
            if (product == null || products.findByActiveTrueOrderByIdAsc().stream().noneMatch(p -> ID.equals(p.id()))) return null;
            if (product.facts().stream().noneMatch(f -> supports(f.evidence()))) return null;
            var updated = jdbc.update("""
                    insert into catalog_spec_attempts(product_id,next_attempt_at) values (?,?)
                    on conflict(product_id) do update set next_attempt_at=excluded.next_attempt_at
                    where catalog_spec_attempts.next_attempt_at <= ?
                    """, ID, java.sql.Timestamp.from(now.plus(Duration.ofDays(7))), java.sql.Timestamp.from(now));
            return updated == 1 ? product.revision() : null;
        });
        if (revision == null) return;
        final String html;
        try { html = source.fetchT17App(); }
        catch (Exception e) { log.warn("catalog_spec_refresh product_id={} outcome=SOURCE_UNAVAILABLE", ID); return; }
        tx.executeWithoutResult(status -> {
            var product = products.findById(ID).orElse(null);
            if (product == null || product.revision() != revision) return;
            for (var fact : product.facts()) {
                var e = fact.evidence();
                if (!supports(e)) continue;
                if (QcySpecificationVerifier.matchesT17AppForm(html)) {
                    product.reviewedFact(revision, e.name(), now);
                    log.info("catalog_spec_refresh product_id={} field={} outcome=VERIFIED", ID, e.name());
                } else log.warn("catalog_spec_refresh product_id={} field={} outcome=UNVERIFIED", ID, e.name());
            }
            products.save(product);
        });
    }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        var pair = CHECKS.get(e.name());
        return pair != null && URL.equals(e.sourceUrl()) && pair.get(0).equals(e.value()) && pair.get(1).equals(e.quote());
    }
}
