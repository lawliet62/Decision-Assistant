package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class Ht20SpecificationRefreshWorker {
    private static final String URL = "https://qcystore.co.kr/product/detail.html?product_no=1028";
    private final SpecificationRefreshRunner runner;
    public Ht20SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            QcySpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "qcy-ht20",
                source::fetchHt20, Ht20SpecificationRefreshWorker::supports, (html, e) -> QcySpecificationVerifier.matchesHt20Form(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return "형태".equals(e.name()) && "커널형".equals(e.value()) && "커널형".equals(e.quote()) && URL.equals(e.sourceUrl());
    }
}
