package com.decisionassistant;

import com.decisionassistant.guidance.EvidenceWorkflowWorker;
import com.decisionassistant.guidance.FirecrawlWebEvidenceProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("integration") @Testcontainers
@SpringBootTest(properties = {"decision-assistant.catalog.enabled=true", "decision-assistant.catalog.refresh-enabled=true",
        "decision-assistant.catalog.poll-interval=3600000", "decision-assistant.catalog.monthly-limit=1",
        "decision-assistant.web.enabled=true", "decision-assistant.web.api-key=test"})
@ActiveProfiles("test") @Import({DecisionApiIntegrationTest.TestSecurityConfiguration.class, CatalogStartupIntegrationTest.DelayedImport.class})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogStartupIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired JdbcTemplate jdbc;
    @MockitoBean FirecrawlWebEvidenceProvider web;
    @MockitoBean EvidenceWorkflowWorker evidenceWorker;

    @TestConfiguration static class DelayedImport {
        @Bean @Order(Ordered.HIGHEST_PRECEDENCE) ApplicationRunner delayImporter() {
            return args -> Thread.sleep(1500);
        }
    }

    @Test void startupRunsDueWorkAfterDelayedManifestImportWithoutDuplicateCalls() throws Exception {
        var deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline) && jdbc.queryForObject("select count(*) from catalog_observations where outcome <> 'ATTEMPTED'", Integer.class) == 0)
            Thread.sleep(100);
        assertThat(jdbc.queryForObject("select count(*) from catalog_products where active", Integer.class)).isEqualTo(21);
        assertThat(jdbc.queryForObject("select count(*) from catalog_observations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select calls from catalog_monthly_usage", Integer.class)).isEqualTo(1);
        verify(web, times(1)).scrapeProduct(anyString());
    }
}
