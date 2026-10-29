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
public class AppleSpecificationRefreshWorker {
    private static final String ID = "apple-airpods-pro-3";
    private static final String URL = "https://www.apple.com/kr/airpods-pro/specs/";
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AppleSpecificationRefreshWorker.class);
    private final boolean enabled;
    private final CatalogProperties config;
    private final CatalogProductRepository products;
    private final JdbcTemplate jdbc;
    private final AppleSpecificationSource source;
    private final TransactionTemplate tx;
    public AppleSpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            AppleSpecificationSource source, PlatformTransactionManager manager) {
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
            var updated = jdbc.update("""
                    insert into catalog_spec_attempts(product_id,next_attempt_at) values (?,?)
                    on conflict(product_id) do update set next_attempt_at=excluded.next_attempt_at
                    where catalog_spec_attempts.next_attempt_at <= ?
                    """, ID, java.sql.Timestamp.from(now.plus(Duration.ofDays(7))), java.sql.Timestamp.from(now));
            return updated == 1 ? product.revision() : null;
        });
        if (revision == null) return;
        final String html;
        try { html = source.fetch(); }
        catch (Exception e) { log.warn("catalog_spec_refresh product_id={} outcome=SOURCE_UNAVAILABLE", ID); return; }
        tx.executeWithoutResult(status -> {
            var product = products.findById(ID).orElse(null);
            if (product == null || product.revision() != revision) return;
            var checks = Map.of("ANC 지원", List.of("지원", "액티브 노이즈 캔슬링"),
                    "방수", List.of("IP57", "먼지, 땀, 습기에 강한 방진 및 생활 방수 디자인(IP57)"));
            for (var fact : product.facts()) {
                var e = fact.evidence(); var pair = checks.get(e.name());
                if (pair == null || !URL.equals(e.sourceUrl()) || !pair.get(0).equals(e.value()) || !pair.get(1).equals(e.quote())) continue;
                if (AppleSpecificationVerifier.matches(html, e.name())) {
                    product.reviewedFact(revision, e.name(), now);
                    log.info("catalog_spec_refresh product_id={} field={} outcome=VERIFIED", ID, e.name());
                } else log.warn("catalog_spec_refresh product_id={} field={} outcome=UNVERIFIED", ID, e.name());
            }
            products.save(product);
        });
    }
}
