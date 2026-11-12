package com.decisionassistant;

import com.decisionassistant.guidance.CatalogImporter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@Tag("integration") @Testcontainers
class CatalogBrandMigrationIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test void backfillsExistingCatalogAndPreservesDataWhileRejectingKnownBrandBlanks() throws Exception {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).target("16").load().migrate();
        var jdbc = new JdbcTemplate(source);
        CatalogImporter.Entry[] entries;
        try (var in = getClass().getResourceAsStream("/catalog/earbuds.json")) {
            entries = new ObjectMapper().readValue(in, CatalogImporter.Entry[].class);
        }
        for (var e : entries) {
            jdbc.update("""
                    INSERT INTO catalog_products (id, revision, name, purchase_url, expected_identity, facts,
                        active, price_status, next_attempt_at, fact_reviews)
                    VALUES (?, ?, ?, ?, ?, '[]', ?, 'VERIFIED', '2030-01-01', '{"ANC 지원":"2026-11-01T00:00:00Z"}')
                    """, e.id(), e.revision(), e.name(), e.purchaseUrl(), e.expectedIdentity(), e.active());
        }
        var before = jdbc.queryForList("SELECT to_jsonb(p) - 'version' AS data FROM catalog_products p ORDER BY id");
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForList("SELECT to_jsonb(p) - 'version' - 'brand_name' AS data FROM catalog_products p ORDER BY id"))
                .isEqualTo(before);
        for (var e : entries) {
            assertThat(jdbc.queryForObject("SELECT brand_name FROM catalog_products WHERE id=?", String.class, e.id()))
                    .isEqualTo(e.brandName());
            for (var blank : new String[] {"", " \t\n"}) {
                assertThatThrownBy(() -> jdbc.update("UPDATE catalog_products SET brand_name=? WHERE id=?", blank, e.id()))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            }
        }
        jdbc.update("""
                INSERT INTO catalog_products (id, revision, name, purchase_url, expected_identity, facts, active,
                    price_status, next_attempt_at, brand_name)
                VALUES ('generic-buds', 1, '무선 이어폰', 'https://example.com/generic', 'generic:1', '[]', false,
                    'UNVERIFIED', now(), '')
                """);
        assertThat(jdbc.queryForObject("SELECT brand_name FROM catalog_products WHERE id='generic-buds'", String.class)).isEmpty();
        assertThatThrownBy(() -> jdbc.update("UPDATE catalog_products SET name='soundcore Buds' WHERE id='generic-buds'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
