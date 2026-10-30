package com.decisionassistant.guidance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
@Component
public class AnkerP41iSpecificationRefreshWorker {
    private final SpecificationRefreshRunner runner;
    public AnkerP41iSpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            AnkerP41iSpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "anker-p41i-a3937",
                source::fetch, AnkerP41iSpecificationRefreshWorker::supports, (html, e) -> AnkerP41iVerifier.specification(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return "ANC 지원".equals(e.name()) && "지원".equals(e.value()) && AnkerP41iVerifier.QUOTE.equals(e.quote())
                && AnkerP41iVerifier.SPEC_URL.equals(e.sourceUrl());
    }
}
