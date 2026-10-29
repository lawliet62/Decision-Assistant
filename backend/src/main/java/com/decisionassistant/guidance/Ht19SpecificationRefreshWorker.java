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
public class Ht19SpecificationRefreshWorker {
    private static final String ID = "qcy-ht19";
    private static final String URL = "https://www.qcy.com/products/qcy-melobuds-n60";
    private static final Map<String, List<String>> CHECKS = Map.of(
            "방수", List.of("IPX5", "Water Resistance Range: IPX5"),
            "ANC 지원", List.of("지원", "Bluetooth 6.0, 56dB ANC, Hi-Res, 45-Hour Playtime, HT19"));
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Ht19SpecificationRefreshWorker.class);
    private final boolean enabled;
    private final CatalogProperties config;
    private final CatalogProductRepository products;
    private final JdbcTemplate jdbc;
    private final QcySpecificationSource source;
    private final TransactionTemplate tx;
    public Ht19SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
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
        try { html = source.fetchN60(); }
        catch (Exception e) { log.warn("catalog_spec_refresh product_id={} outcome=SOURCE_UNAVAILABLE", ID); return; }
        tx.executeWithoutResult(status -> {
            var product = products.findById(ID).orElse(null);
            if (product == null || product.revision() != revision) return;
            for (var fact : product.facts()) {
                var e = fact.evidence();
                if (!supports(e)) continue;
                var matches = "방수".equals(e.name()) ? QcySpecificationVerifier.matchesHt19WaterResistance(html)
                        : QcySpecificationVerifier.matchesHt19ModelAndAnc(html);
                if (matches) {
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
