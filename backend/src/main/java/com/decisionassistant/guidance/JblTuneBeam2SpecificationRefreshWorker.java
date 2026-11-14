package com.decisionassistant.guidance;

@org.springframework.stereotype.Component
public class JblTuneBeam2SpecificationRefreshWorker {
    private final SpecificationRefreshRunner runner;
    public JblTuneBeam2SpecificationRefreshWorker(
            @org.springframework.beans.factory.annotation.Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, org.springframework.jdbc.core.JdbcTemplate jdbc,
            JblTuneBeam2SpecificationSource source, org.springframework.transaction.PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, JblTuneBeam2Verifier.ID,
                source::fetch, JblTuneBeam2Verifier::supports, JblTuneBeam2Verifier::specification);
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
}
