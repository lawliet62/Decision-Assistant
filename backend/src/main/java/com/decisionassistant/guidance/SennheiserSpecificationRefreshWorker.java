package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;

@Component
public class SennheiserSpecificationRefreshWorker {
    private static final String URL = "https://kr.sennheiser-hearing.com/product/detail.html?product_no=731";
    private static final Map<String, List<String>> CHECKS = Map.of(
            "ANC 지원", List.of("지원", "하이브리드 적응형 ANC"),
            "방수", List.of("IP54", "IP54 방진 및 생활 방수 (이어버드만 해당)"));
    private final SpecificationRefreshRunner runner;
    public SennheiserSpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            SennheiserSpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "sennheiser-momentum-tw5",
                source::fetch, SennheiserSpecificationRefreshWorker::supports, (html, e) -> SennheiserSpecificationVerifier.matches(html, e.name()));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        var pair = CHECKS.get(e.name());
        return pair != null && URL.equals(e.sourceUrl()) && pair.get(0).equals(e.value()) && pair.get(1).equals(e.quote());
    }
}
