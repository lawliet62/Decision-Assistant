package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;

@Component
public class Apple5SpecificationRefreshWorker {
    private static final String ID = "apple-airpods-5-wireless";
    private static final String URL = "https://www.apple.com/kr/airpods-5/specs/";
    private static final Map<String, List<String>> CHECKS = Map.of(
            "호환성", List.of("iOS", AppleSpecificationVerifier.IOS_QUOTE),
            "ANC 지원", List.of("지원", "액티브 노이즈 캔슬링"),
            "방수", List.of("IP57", "먼지, 땀, 습기에 강한 방진 및 생활 방수 디자인(IP57)"));
    private final SpecificationRefreshRunner runner;
    public Apple5SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            AppleSpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, ID,
                source::fetchAirpods5, Apple5SpecificationRefreshWorker::supports, (html, e) -> AppleSpecificationVerifier.matchesAirpods5(html, e.name()));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        var pair = CHECKS.get(e.name());
        return pair != null && URL.equals(e.sourceUrl()) && pair.get(0).equals(e.value()) && pair.get(1).equals(e.quote());
    }
}
