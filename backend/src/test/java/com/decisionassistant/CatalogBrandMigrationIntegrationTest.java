package com.decisionassistant;

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
    // V17 이전 등록 제품의 고정 fixture다. 이후 추가된 브랜드를 과거 DB에 소급 삽입하지 않는다.
    private record LegacyCatalogEntry(String id, int revision, String name, String purchaseUrl,
                                      String expectedIdentity, boolean active, String brandName) {}
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test void backfillsExistingCatalogAndPreservesDataWhileRejectingKnownBrandBlanks() throws Exception {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).target("16").load().migrate();
        var jdbc = new JdbcTemplate(source);
        LegacyCatalogEntry[] entries;
        try (var in = getClass().getResourceAsStream("/catalog/pre-brand-products.json")) {
            entries = new ObjectMapper().readValue(in, LegacyCatalogEntry[].class);
        }
        for (var e : entries) {
            jdbc.update("""
                    INSERT INTO catalog_products (id, revision, name, purchase_url, expected_identity, facts,
                        active, price_status, next_attempt_at, fact_reviews)
                    VALUES (?, ?, ?, ?, ?, '[]', ?, 'VERIFIED', '2030-01-01', '{"ANC 지원":"2026-11-01T00:00:00Z"}')
                    """, e.id(), e.revision(), e.name(), e.purchaseUrl(), e.expectedIdentity(), e.active());
        }
        var before = jdbc.queryForList("SELECT to_jsonb(p) - 'version' AS data FROM catalog_products p ORDER BY id");
        Flyway.configure().dataSource(source).target("18").load().migrate();
        // 과거 예약값을 실제 브랜드명과 구분해 이전한다.
        jdbc.update("""
                INSERT INTO catalog_products (id, revision, name, purchase_url, expected_identity, facts, active,
                    price_status, next_attempt_at, brand_name)
                VALUES ('legacy-generic', 1, '이어폰', 'https://example.com/legacy', 'generic:0', '[]', false,
                    'UNVERIFIED', now(), '무브랜드')
                """);
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForObject("SELECT brand_status FROM catalog_products WHERE id='legacy-generic'", String.class)).isEqualTo("UNBRANDED");
        assertThat(jdbc.queryForObject("SELECT brand_name FROM catalog_products WHERE id='legacy-generic'", String.class)).isNull();
        assertThat(jdbc.queryForList("SELECT to_jsonb(p) - 'version' - 'brand_name' - 'brand_status' AS data FROM catalog_products p WHERE id<>'legacy-generic' ORDER BY id"))
                .isEqualTo(before);
        for (var e : entries) {
            assertThat(jdbc.queryForObject("SELECT brand_name FROM catalog_products WHERE id=?", String.class, e.id()))
                    .isEqualTo(e.brandName());
            assertThat(jdbc.queryForObject("SELECT brand_status FROM catalog_products WHERE id=?", String.class, e.id())).isEqualTo("KNOWN");
            for (var blank : new String[] {null, "", " \t\n"}) {
                assertThatThrownBy(() -> jdbc.update("UPDATE catalog_products SET brand_name=? WHERE id=?", blank, e.id()))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            }
        }
        jdbc.update("""
                INSERT INTO catalog_products (id, revision, name, purchase_url, expected_identity, facts, active,
                    price_status, next_attempt_at, brand_name, brand_status)
                VALUES ('generic-buds', 1, '무선 이어폰', 'https://example.com/generic', 'generic:1', '[]', false,
                    'UNVERIFIED', now(), NULL, 'UNBRANDED')
                """);
        assertThat(jdbc.queryForObject("SELECT brand_name FROM catalog_products WHERE id='generic-buds'", String.class)).isNull();
        for (var blank : new String[] {"Anker", "", " \t\n"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE catalog_products SET brand_name=? WHERE id='generic-buds'", blank))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE catalog_products SET name='soundcore Buds' WHERE id='generic-buds'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE catalog_products SET name='JBL Buds' WHERE id='generic-buds'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("UPDATE catalog_products SET brand_status='UNVERIFIED', name='soundcore Buds' WHERE id='generic-buds'");
        assertThatThrownBy(() -> jdbc.update("UPDATE catalog_products SET brand_status='KNOWN' WHERE id='generic-buds'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("UPDATE catalog_products SET brand_status='KNOWN', brand_name='노브랜드' WHERE id='generic-buds'");
        assertThatThrownBy(() -> jdbc.update("UPDATE catalog_products SET brand_status='INVALID' WHERE id='generic-buds'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO catalog_products (id, revision, name, purchase_url, expected_identity, facts, active,
                    price_status, next_attempt_at)
                VALUES ('missing-brand', 1, '무선 이어폰', 'https://example.com/missing', 'generic:2', '[]', false,
                    'UNVERIFIED', now())
                """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
