package com.decisionassistant;

import com.decisionassistant.guidance.*;
import com.decisionassistant.lookup.SerperSearchProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static com.decisionassistant.guidance.DiscoveryContracts.ProductDiscoverer;
import static org.mockito.Mockito.*;

/** 실제 Spring scheduler와 DB를 사용하며 모든 외부 출처는 mock이다. */
@Tag("integration") @Testcontainers
@SpringBootTest(properties = {"decision-assistant.catalog.enabled=true", "decision-assistant.catalog.manifest=classpath:catalog/earbuds.json",
        "decision-assistant.catalog.refresh-enabled=true", "decision-assistant.catalog.poll-interval=200",
        "decision-assistant.catalog.spec-refresh-enabled=true", "decision-assistant.catalog.spec-poll-interval=200",
        "decision-assistant.catalog.monthly-limit=20", "decision-assistant.web.enabled=true", "decision-assistant.web.api-key=test"})
@ActiveProfiles("test") @Import(DecisionApiIntegrationTest.TestSecurityConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogSchedulingIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired JdbcTemplate jdbc;
    @MockitoBean QcySpecificationSource qcy;
    @MockitoBean AppleSpecificationSource apple;
    @MockitoBean MarshallSpecificationSource marshall;
    @MockitoBean SennheiserSpecificationSource sennheiser;
    @MockitoBean AnkerP31iSpecificationSource anker;
    @MockitoBean AnkerAeroclipSpecificationSource aeroclip;
    @MockitoBean AnkerLibertySpecificationSource liberty;
    @MockitoBean AnkerLibertyProSpecificationSource libertyPro;
    @MockitoBean AnkerP41iSpecificationSource p41i;
    @MockitoBean AudioTechnicaSpecificationRefreshWorker audioTechnicaWorker;
    @MockitoBean AudioTechnicaAc5SpecificationRefreshWorker audioTechnicaAc5Worker;
    @MockitoBean BoseQcSpecificationRefreshWorker boseQcWorker;
    @MockitoBean SamsungBuds4SpecificationSource samsung;
    @MockitoBean JblTuneBeam2SpecificationSource jbl;
    @MockitoBean FirecrawlWebEvidenceProvider web;
    @MockitoBean SerperSearchProvider search;
    @MockitoBean ProductDiscoverer ai;
    @MockitoBean EvidenceWorkflowWorker evidenceWorker;

    @Test void scheduledWorkersReserveOnceAndKeepTheirNextAttemptAfterFailures() throws Exception {
        var deadline = Instant.now().plus(Duration.ofSeconds(25));
        int priceAttempts = 0, specAttempts = 0;
        while (Instant.now().isBefore(deadline)) {
            priceAttempts = jdbc.queryForObject("select count(*) from catalog_observations where outcome='COLLECTION_FAILED'", Integer.class);
            specAttempts = jdbc.queryForObject("select count(*) from catalog_spec_attempts", Integer.class);
            if (priceAttempts == 20 && specAttempts == 21) break;
            Thread.sleep(100);
        }
        assertThat(priceAttempts).isEqualTo(20);
        assertThat(specAttempts).isEqualTo(21);
        Thread.sleep(1000); // 여러 scheduler tick에서 재시도가 발생하지 않는지 확인한다.
        assertThat(jdbc.queryForObject("select count(*) from catalog_observations", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("select calls from catalog_monthly_usage", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from catalog_products where active and next_attempt_at >= last_attempt_at + interval '48 hours'", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where next_attempt_at > now() + interval '6 days'", Integer.class)).isEqualTo(21);
        verify(web, times(20)).scrapeProduct(anyString());
    }
}
