package com.decisionassistant.guidance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.UUID;

@Component
public class CatalogRefreshWorker {
    private final CatalogProperties config;
    private final WebProperties web;
    private final FirecrawlWebEvidenceProvider provider;
    private final CatalogProductRepository products;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public CatalogRefreshWorker(CatalogProperties config, WebProperties web, FirecrawlWebEvidenceProvider provider,
                                CatalogProductRepository products, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.config = config; this.web = web; this.provider = provider; this.products = products; this.jdbc = jdbc;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    record Work(String id, String url, String identity, int revision, UUID observation) {}
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.poll-interval:60000}")
    public void refreshNext() {
        if (!config.enabled() || !config.refreshEnabled() || !web.configured() || config.monthlyLimit() <= 0) return;
        var now = Instant.now();
        var work = tx.execute(status -> {
            var product = products.claimNext(now).orElse(null);
            if (product == null) return null;
            // 외부 요청 전에 quota와 당일 시도를 영속화한다. 재시작·동시 실행·실패도 한도를 공유한다.
            var reserved = jdbc.update("""
                    insert into catalog_monthly_usage(month, calls) values (?, 1)
                    on conflict (month) do update set calls = catalog_monthly_usage.calls + 1
                    where catalog_monthly_usage.calls < ?
                    """, YearMonth.now(ZoneOffset.UTC).toString(), Math.max(0, config.monthlyLimit()));
            if (reserved == 0) return null;
            product.attempted(now);
            var observation = UUID.randomUUID();
            jdbc.update("insert into catalog_observations(id,product_id,attempted_at,outcome) values (?,?,?,'ATTEMPTED')",
                    observation, product.id(), java.sql.Timestamp.from(now));
            return new Work(product.id(), product.purchaseUrl(), product.expectedIdentity(), product.revision(), observation);
        });
        if (work == null) return;
        OfficialProductParser.Result inspected = null;
        String outcome;
        try {
            inspected = OfficialProductParser.inspect(work.url(), provider.scrapeProduct(work.url()), Instant.now());
            outcome = inspected.product() == null ? inspected.rejection().name()
                    : work.identity().equals(inspected.product().identity()) ? "VERIFIED" : "MODEL_MISMATCH";
        } catch (RuntimeException ex) { outcome = "COLLECTION_FAILED"; }
        var result = inspected;
        var code = outcome;
        tx.executeWithoutResult(status -> {
            var product = products.findById(work.id()).orElseThrow();
            var effective = product.revision() != work.revision() ? "REGISTRATION_CHANGED" : code;
            if ("VERIFIED".equals(effective)) product.observed(result.product());
            else if (!"REGISTRATION_CHANGED".equals(effective)) product.rejected(effective);
            jdbc.update("update catalog_observations set completed_at=?,outcome=?,price=?,currency=? where id=?",
                    java.sql.Timestamp.from(Instant.now()), effective,
                    "VERIFIED".equals(effective) ? result.product().offer().price() : null,
                    "VERIFIED".equals(effective) ? "KRW" : null, work.observation());
        });
    }
}
