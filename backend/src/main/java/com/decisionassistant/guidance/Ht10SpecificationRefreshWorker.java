package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class Ht10SpecificationRefreshWorker {
    private static final String URL = "https://qcystore.co.kr/product/detail.html?product_no=924";
    private final SpecificationRefreshRunner runner;
    public Ht10SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            QcySpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "qcy-ht10-hires",
                source::fetchHt10, Ht10SpecificationRefreshWorker::supports, (html, e) -> QcySpecificationVerifier.matchesHt10Anc(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return "ANC 지원".equals(e.name()) && "지원".equals(e.value()) && "ANC APP 노이즈캔슬링".equals(e.quote()) && URL.equals(e.sourceUrl());
    }
}
