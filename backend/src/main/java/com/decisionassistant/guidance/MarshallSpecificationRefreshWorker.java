package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;

@Component
public class MarshallSpecificationRefreshWorker {
    private static final String ID = "marshall-motif-ii-anc";
    private static final String URL = "https://www.marshall.com/kr/ko/product/motif-ii-anc?pid=1006450";
    private static final Map<String, List<String>> CHECKS = Map.of(
            "ANC 지원", List.of("지원", "액티브 노이즈 캔슬링"));
    private final SpecificationRefreshRunner runner;
    public MarshallSpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            MarshallSpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, ID,
                source::fetch, MarshallSpecificationRefreshWorker::supports, (html, e) -> MarshallSpecificationVerifier.matches(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        var pair = CHECKS.get(e.name());
        return pair != null && URL.equals(e.sourceUrl()) && pair.get(0).equals(e.value()) && pair.get(1).equals(e.quote());
    }
}
