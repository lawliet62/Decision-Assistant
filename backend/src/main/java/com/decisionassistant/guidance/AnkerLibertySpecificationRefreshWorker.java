package com.decisionassistant.guidance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class AnkerLibertySpecificationRefreshWorker {
    private final SpecificationRefreshRunner runner;
    public AnkerLibertySpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            AnkerLibertySpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "anker-liberty5-a3957",
                source::fetch, AnkerLibertySpecificationRefreshWorker::supports, (html, e) -> AnkerLibertySpecificationVerifier.matches(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return "ANC 지원".equals(e.name()) && "지원".equals(e.value()) && AnkerLibertySpecificationVerifier.QUOTE.equals(e.quote())
                && AnkerLibertySpecificationVerifier.URL.equals(e.sourceUrl());
    }
}
