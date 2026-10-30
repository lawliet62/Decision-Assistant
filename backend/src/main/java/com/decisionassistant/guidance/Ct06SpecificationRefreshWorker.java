package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class Ct06SpecificationRefreshWorker {
    private final SpecificationRefreshRunner runner;
    public Ct06SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            QcySpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "qcy-ct06",
                source::fetchCt06, Ct06SpecificationRefreshWorker::supports, (html, e) -> QcySpecificationVerifier.matchesCt06Form(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return "형태".equals(e.name()) && "오픈형".equals(e.value()) && "오픈형".equals(e.quote()) && Ct06VariantVerifier.URL.equals(e.sourceUrl());
    }
}
