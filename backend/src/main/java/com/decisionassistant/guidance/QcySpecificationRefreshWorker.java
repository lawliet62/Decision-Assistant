package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;

/** 승인된 단일 모델의 명시된 사양만 재확인한다. */
@Component
public class QcySpecificationRefreshWorker {
    private static final String ID = "qcy-t13-pro";
    private static final String URL = "https://www.qcy.com/products/qcy-t13-pro";
    private static final Map<String, List<String>> CHECKS = Map.of(
            "호환성", List.of("Compatible Systems", "iOS / Android"),
            "방수", List.of("Water Resistance Range", "IPX4"));
    private final SpecificationRefreshRunner runner;
    public QcySpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            QcySpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, ID,
                source::fetch, QcySpecificationRefreshWorker::supports, QcySpecificationRefreshWorker::matches);
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        if (!URL.equals(e.sourceUrl())) return false;
        if ("ANC 지원".equals(e.name())) return "지원".equals(e.value()) && "50dB Adaptive ANC".equals(e.quote());
        var pair = CHECKS.get(e.name());
        return pair != null && pair.get(1).equals(e.value()) && (pair.get(0) + ": " + pair.get(1)).equals(e.quote());
    }
    private static boolean matches(String html, DiscoveryContracts.ProductFact e) {
        if ("ANC 지원".equals(e.name())) return QcySpecificationVerifier.matchesAnc(html);
        var pair = CHECKS.get(e.name());
        return QcySpecificationVerifier.matches(html, "QCY T13 Pro", pair.get(0), pair.get(1));
    }
}
