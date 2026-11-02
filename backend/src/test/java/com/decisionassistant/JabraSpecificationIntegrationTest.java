package com.decisionassistant;

import com.decisionassistant.guidance.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.List;
import static com.decisionassistant.guidance.DiscoveryContracts.ProductFact;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("integration") @Testcontainers
@SpringBootTest(properties = {"decision-assistant.catalog.enabled=true", "decision-assistant.catalog.refresh-enabled=false", "decision-assistant.catalog.spec-refresh-enabled=false"})
@ActiveProfiles("test") @Import(DecisionApiIntegrationTest.TestSecurityConfiguration.class)
class JabraSpecificationIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired CatalogProperties config;
    @Autowired CatalogProductRepository products;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean JabraEvolve2SpecificationSource source;

    @Test void reviewPersistsOnlyOncePerWeek() throws Exception {
        var id = "jabra-evolve2-buds-usba";
        var url = "https://www.jabra.com/ko-kr/about/news-and-press-releases/2022-jabra-evolve2-buds";
        var title = "Jabra unveils professional, pocketable true wireless earbuds engineered for hybrid and remote working";
        var fact = new ProductFact("ANC 지원", "지원", url, "Adjustable Active Noise Cancellation", Instant.now().minus(Duration.ofDays(10)), "UNREVIEWED");
        var product = new CatalogProduct(id);
        product.register(1, "Jabra Evolve2 Buds USB-A", "https://www.jabramall.com/product/detail.html?product_no=2162", "jabra-kr:2162-usba",
                List.of(new CatalogProduct.Fact(fact, CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(product);
        when(source.fetch()).thenReturn("<title>" + title + "</title><link rel='canonical' href='" + url + "'><div><h1>" + title
                + "</h1><ul><li>Launches new Evolve2 Buds</li><li><strong>Adjustable Active Noise Cancellation</strong> - filters out background noise</li></ul></div>");
        var worker = new JabraEvolve2SpecificationRefreshWorker(true, config, products, jdbc, source, manager);
        worker.refresh(); worker.refresh();
        verify(source, times(1)).fetch();
        assertThat(jdbc.queryForObject("select jsonb_exists(fact_reviews, 'ANC 지원') from catalog_products where id=?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=? and next_attempt_at > now() + interval '6 days'", Integer.class, id)).isEqualTo(1);
    }
}
