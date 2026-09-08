package com.decisionassistant.lookup;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@Testcontainers
class SearchQuotaIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test void capIsAtomicDurableAndResetsByUtcMonth() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
        var properties = new SearchProperties(true, "test", 5);
        var quota = new SearchQuota(jdbc, properties);
        var month = LocalDate.of(2026, 9, 1);
        var successes = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = java.util.stream.IntStream.range(0, 20).mapToObj(i -> executor.submit(() -> {
                try { quota.reserveMonth(month); successes.incrementAndGet(); }
                catch (LookupException expected) { assertThat(expected.code()).isEqualTo("SEARCH_MONTHLY_LIMIT"); }
            })).toList();
            for (var task : tasks) task.get();
        }
        assertThat(successes.get()).isEqualTo(5);
        assertThatThrownBy(() -> new SearchQuota(jdbc, properties).reserveMonth(month)).isInstanceOf(LookupException.class);
        quota.reserveMonth(month.plusMonths(1));
        assertThat(jdbc.queryForObject("SELECT calls FROM search_monthly_usage WHERE month = ?", Integer.class, month)).isEqualTo(5);
    }
}
