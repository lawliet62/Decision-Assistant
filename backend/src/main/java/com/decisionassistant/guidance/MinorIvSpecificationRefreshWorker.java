package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class MinorIvSpecificationRefreshWorker {
    private static final String URL = "https://www.marshall.com/kr/ko/product/minor-iv?pid=1006653";
    private final SpecificationRefreshRunner runner;
    public MinorIvSpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            MarshallSpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "marshall-minor-iv-black",
                source::fetchMinorIv, MinorIvSpecificationRefreshWorker::supports, (html, e) -> MarshallSpecificationVerifier.matchesMinorIvWater(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return "방수".equals(e.name()) && "IPX4".equals(e.value()) && "이어버드 IPX4".equals(e.quote()) && URL.equals(e.sourceUrl());
    }
}
