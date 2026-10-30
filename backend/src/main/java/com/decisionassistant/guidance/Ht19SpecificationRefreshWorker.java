package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;

@Component
public class Ht19SpecificationRefreshWorker {
    private static final String ID = "qcy-ht19";
    private static final String URL = "https://www.qcy.com/products/qcy-melobuds-n60";
    private static final Map<String, List<String>> CHECKS = Map.of(
            "방수", List.of("IPX5", "Water Resistance Range: IPX5"),
            "ANC 지원", List.of("지원", "Bluetooth 6.0, 56dB ANC, Hi-Res, 45-Hour Playtime, HT19"));
    private final SpecificationRefreshRunner runner;
    public Ht19SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            QcySpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, ID,
                source::fetchN60, Ht19SpecificationRefreshWorker::supports, (html, e) -> "방수".equals(e.name())
                        ? QcySpecificationVerifier.matchesHt19WaterResistance(html) : QcySpecificationVerifier.matchesHt19ModelAndAnc(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        var pair = CHECKS.get(e.name());
        return pair != null && URL.equals(e.sourceUrl()) && pair.get(0).equals(e.value()) && pair.get(1).equals(e.quote());
    }
}
