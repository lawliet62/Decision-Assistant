package com.decisionassistant.guidance;
@org.springframework.stereotype.Component
public class SamsungBuds4SpecificationRefreshWorker {
    private final SpecificationRefreshRunner runner;
    public SamsungBuds4SpecificationRefreshWorker(
            @org.springframework.beans.factory.annotation.Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config,CatalogProductRepository products,org.springframework.jdbc.core.JdbcTemplate jdbc,
            SamsungBuds4SpecificationSource source,org.springframework.transaction.PlatformTransactionManager manager) {
        runner=new SpecificationRefreshRunner(enabled,config,products,jdbc,manager,SamsungBuds4Verifier.ID,
                source::fetch,SamsungBuds4Verifier::supports,SamsungBuds4Verifier::specification);
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh(){runner.refresh();}
}
