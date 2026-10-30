package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class Ht08SpecificationRefreshWorker {
    private static final String URL = "https://qcystore.co.kr/article/%EB%A7%A4%EA%B1%B0%EC%A7%84/8/16170/";
    private final SpecificationRefreshRunner runner;
    public Ht08SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            QcySpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "qcy-ht08-pro-plus",
                source::fetchHt08Article, Ht08SpecificationRefreshWorker::supports, (html, e) -> QcySpecificationVerifier.matchesHt08Anc(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return "ANC 지원".equals(e.name()) && "지원".equals(e.value()) && "적응형 액티브 노이즈 캔슬링 기능".equals(e.quote()) && URL.equals(e.sourceUrl());
    }
}
