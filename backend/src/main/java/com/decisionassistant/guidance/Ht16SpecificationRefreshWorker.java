package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;

@Component
public class Ht16SpecificationRefreshWorker {
    private static final String ID = "qcy-ht16";
    private static final String URL = "https://qcystore.co.kr/product/detail.html?product_no=990";
    private static final Map<String, List<String>> CHECKS = Map.of(
            "ANC 지원", List.of("지원", "ANC 노이즈캔슬링"));
    private final SpecificationRefreshRunner runner;
    public Ht16SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            QcySpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, ID,
                source::fetchHt16, Ht16SpecificationRefreshWorker::supports, (html, e) -> QcySpecificationVerifier.matchesHt16Anc(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        var pair = CHECKS.get(e.name());
        return pair != null && URL.equals(e.sourceUrl()) && pair.get(0).equals(e.value()) && pair.get(1).equals(e.quote());
    }
}
