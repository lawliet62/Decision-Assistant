package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class T29SpecificationRefreshWorker {
    private static final String URL = "https://qcystore.co.kr/product/detail.html?product_no=709";
    private final SpecificationRefreshRunner runner;
    public T29SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            QcySpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "qcy-t29-app",
                source::fetchT29, T29SpecificationRefreshWorker::supports, (html, e) -> QcySpecificationVerifier.matchesT29Form(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return "형태".equals(e.name()) && "오픈형".equals(e.value()) && "오픈형".equals(e.quote()) && URL.equals(e.sourceUrl());
    }
}
