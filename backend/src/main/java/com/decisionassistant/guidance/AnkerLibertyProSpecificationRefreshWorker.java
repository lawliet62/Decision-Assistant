package com.decisionassistant.guidance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class AnkerLibertyProSpecificationRefreshWorker {
    private final SpecificationRefreshRunner runner;
    public AnkerLibertyProSpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc,
            AnkerLibertyProSpecificationSource source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "anker-liberty5-pro-d1203",
                source::fetch, AnkerLibertyProSpecificationRefreshWorker::supports, (html, e) -> "ANC 지원".equals(e.name())
                        ? AnkerLibertyProSpecificationVerifier.matchesAnc(html) : AnkerLibertyProSpecificationVerifier.matches(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return AnkerLibertyProSpecificationVerifier.URL.equals(e.sourceUrl())
                && ("방수".equals(e.name()) && "IP55".equals(e.value()) && AnkerLibertyProSpecificationVerifier.QUOTE.equals(e.quote())
                || "ANC 지원".equals(e.name()) && "지원".equals(e.value()) && AnkerLibertyProSpecificationVerifier.ANC_QUOTE.equals(e.quote()));
    }
}
