package com.decisionassistant.guidance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class AnkerAeroclipSpecificationRefreshWorker {
    private final SpecificationRefreshRunner runner;
    public AnkerAeroclipSpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            AnkerAeroclipSpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "anker-aeroclip-a3388",
                source::fetch, AnkerAeroclipSpecificationRefreshWorker::supports, (html, e) -> AnkerAeroclipSpecificationVerifier.matches(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return "형태".equals(e.name()) && "오픈형".equals(e.value()) && "오픈형".equals(e.quote())
                && AnkerProductVerifier.AERO_URL.equals(e.sourceUrl());
    }
}
