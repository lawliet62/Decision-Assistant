package com.decisionassistant;

import com.decisionassistant.guidance.*;
import com.decisionassistant.identity.*;
import com.decisionassistant.lookup.SerperSearchProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration") @Testcontainers
@SpringBootTest(properties = {"decision-assistant.catalog.enabled=true", "decision-assistant.catalog.manifest=classpath:catalog/earbuds.json",
        "decision-assistant.catalog.refresh-enabled=true", "decision-assistant.catalog.poll-interval=3600000",
        "decision-assistant.catalog.monthly-limit=2", "decision-assistant.web.enabled=true", "decision-assistant.web.api-key=test"})
@AutoConfigureMockMvc @ActiveProfiles("test")
@Import(DecisionApiIntegrationTest.TestSecurityConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogApiIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mvc;
    @Autowired ApplicationUserRepository users;
    @Autowired CatalogProductRepository products;
    @Autowired CatalogRefreshWorker worker;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SerperSearchProvider search;
    @MockitoBean FirecrawlWebEvidenceProvider web;
    @MockitoBean ProductDiscoverer ai;
    @MockitoBean EvidenceWorkflowWorker evidenceWorker;
    final ObjectMapper json = new ObjectMapper();
    RequestPostProcessor owner;
    private static final String BODY = """
            {"templateKey":"EARBUDS","intentText":"비공개 구매 메모",
            "discovery":{"templateVersion":"intake-catalog-v1","uses":["출퇴근"],"maxBudget":50000,
            "compatibility":"Android","minimumRequirements":{"anc":"required"},
            "searchConsent":false,"collectionConsent":false,"aiConsent":false,"sensitiveDataAcknowledged":false}}
            """;
    @BeforeEach void setup() {
        jdbc.update("delete from catalog_observations"); jdbc.update("delete from catalog_products"); jdbc.update("delete from catalog_monthly_usage");
        owner = owner();
    }
    private RequestPostProcessor owner() {
        var id = UUID.randomUUID(); var principal = new AppUserPrincipal(id, id + "@example.com", "Test");
        users.save(new ApplicationUser(id, principal.email(), principal.email(), "Test", Instant.now()));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
    private CatalogProduct product(int n, Instant priceAt) {
        var p = new CatalogProduct("product-" + n); var now = Instant.now(); var url = url(n);
        p.register(1, "Buds " + n, url, "qcy-kr:buds " + n, List.of(
                new CatalogProduct.Fact(new ProductFact("ANC 지원", "지원", url, "ANC 지원", now, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE),
                new CatalogProduct.Fact(new ProductFact("호환성", "Android", url, "Android 지원", now, "UNREVIEWED"), CatalogProduct.ReviewCycle.SOFTWARE)), true);
        if (priceAt != null) p.observed(new OfficialProduct(p.name(), p.expectedIdentity(), new OfficialOffer(url, "QCY 공식몰", BigDecimal.valueOf(20000 + n), "KRW", null, priceAt, "판매가", "catalog-v1"), "", "판매가 " + (20000 + n) + "원"));
        return products.saveAndFlush(p);
    }
    private String url(int n) { return "https://qcystore.co.kr/product/buds/" + (1000 + n) + "/"; }
    private String create() throws Exception {
        var result = mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).path("id").asText();
    }
    @Test void catalogWorksWithoutExternalConsentAndPreservesOwnershipAndHistoricalFacts() throws Exception {
        var p = product(1, Instant.now()); product(2, Instant.now().minus(Duration.ofDays(2))); product(3, null);
        var id = create();
        var result = mvc.perform(get("/api/v1/decisions/" + id).with(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.discovery.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.discovery.policyVersion").value("catalog-v1"))
                .andExpect(jsonPath("$.guidance.aiConsent").value(false))
                .andExpect(jsonPath("$.guidance.webConsent").value(false))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].recommendationGroup").value("RECOMMENDED"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[1].priceStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[2].recommendationGroup").value("UNKNOWN"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[2].compatibilityCheck.status").value("MEETS"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/decisions/" + id).with(owner())).andExpect(status().isNotFound());
        var candidate = json.readTree(result).path("guidance").path("candidateSuggestions").get(0).path("id").asText();
        mvc.perform(patch("/api/v1/decisions/" + id + "/candidate-suggestions/" + candidate).with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isOk());
        p.register(2, p.name(), p.purchaseUrl(), p.expectedIdentity(), List.of(), true); products.saveAndFlush(p);
        mvc.perform(get("/api/v1/decisions/" + id).with(owner)).andExpect(jsonPath("$.productFacts[0].facts.length()").value(3));
        verifyNoInteractions(search, web, ai);
    }
    @Test void expiredPriceNeverPassesBudgetEvenWhenSpecsMeetRequirements() throws Exception {
        product(1, Instant.now().minus(Duration.ofHours(25)));
        var id = create();
        mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].recommendationGroup").value("UNKNOWN"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].reason").value(org.hamcrest.Matchers.containsString("예산 충족 여부를 판정하지 않았습니다")));
    }
    @Test void refreshBudgetSurvivesFailureAndRepeatedTicks() {
        product(1, Instant.now().minus(Duration.ofDays(2))); product(2, null); product(3, null);
        when(web.scrapeProduct(anyString())).thenThrow(new IllegalStateException("private provider error"));
        worker.refreshNext(); worker.refreshNext(); worker.refreshNext(); worker.refreshNext();
        verify(web, times(2)).scrapeProduct(anyString());
        assertThat(jdbc.queryForObject("select sum(calls) from catalog_monthly_usage", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from catalog_observations where outcome='COLLECTION_FAILED'", Integer.class)).isEqualTo(2);
        assertThat(CatalogDiscovery.fresh(products.findById("product-1").orElseThrow().usableOffer(), Instant.now())).isFalse();
    }
    @Test void concurrentWorkersDoNotCollectTheSameProductTwice() throws Exception {
        product(1, null);
        when(web.scrapeProduct(anyString())).thenReturn(page(1, "Buds 1"));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = List.<java.util.concurrent.Callable<Void>>of(() -> { worker.refreshNext(); return null; }, () -> { worker.refreshNext(); return null; });
            for (var result : executor.invokeAll(tasks)) result.get();
        }
        verify(web, times(1)).scrapeProduct(url(1));
        assertThat(jdbc.queryForObject("select sum(calls) from catalog_monthly_usage", Integer.class)).isEqualTo(1);
    }
    @Test void validatedPriceUpdatesWhileDifferentModelInvalidatesOldOffer() {
        product(1, Instant.now()); product(2, Instant.now());
        when(web.scrapeProduct(url(1))).thenReturn(page(1, "Buds 1"));
        when(web.scrapeProduct(url(2))).thenReturn(page(2, "Other model"));
        worker.refreshNext(); worker.refreshNext();
        assertThat(products.findById("product-1").orElseThrow().usableOffer().price()).isEqualByComparingTo("35000");
        assertThat(products.findById("product-2").orElseThrow().usableOffer()).isNull();
        assertThat(jdbc.queryForList("select outcome from catalog_observations", String.class)).containsExactlyInAnyOrder("VERIFIED", "MODEL_MISMATCH");
    }
    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void largePriceChangeIsLoggedButStillUpdatesVerifiedOffer(org.springframework.boot.test.system.CapturedOutput output) {
        var p = product(1, Instant.now());
        p.observed(new OfficialProduct(p.name(), p.expectedIdentity(), new OfficialOffer(url(1), "QCY 공식몰",
                BigDecimal.valueOf(10000), "KRW", null, Instant.now(), "판매가", "catalog-v1"), "", "판매가 10000원"));
        products.saveAndFlush(p);
        when(web.scrapeProduct(url(1))).thenReturn(page(1, "Buds 1"));
        worker.refreshNext();
        assertThat(products.findById(p.id()).orElseThrow().usableOffer().price()).isEqualByComparingTo("35000");
        assertThat(output.getAll()).contains("catalog_price_change product_id=product-1 reason=PRICE_DOUBLED_OR_HALVED");
    }
    private WebEvidenceProvider.Page page(int n, String name) {
        var data = json.writeValueAsString(Map.of("@type", "Product", "name", name, "sku", name, "url", url(n), "offers", Map.of("@type", "Offer", "priceCurrency", "KRW", "price", 35000)));
        var html = "<script type='application/ld+json'>" + data + "</script><article itemscope itemtype='https://schema.org/Product'><h1 itemprop='name'>" + name
                + "</h1><table><tr><th>판매가</th><td>35,000원</td></tr></table></article>";
        return new WebEvidenceProvider.Page(name, "판매가 35,000원", html, url(n));
    }
    @Test void measureDatabaseBaselineAtFiveAndThirtyProductsWithoutCache() throws Exception {
        var reports = new ArrayList<Map<String, Object>>();
        for (var count : List.of(5, 30)) {
            for (int i = 1; i <= count; i++) product(i, Instant.now());
            for (int i = 0; i < 5; i++) create();
            for (var concurrency : List.of(1, 5)) {
                var samples = new ArrayList<Double>();
                try (var executor = java.util.concurrent.Executors.newFixedThreadPool(concurrency)) {
                    var tasks = new ArrayList<java.util.concurrent.Callable<Double>>();
                    for (int i = 0; i < 30; i++) tasks.add(() -> { var start = System.nanoTime(); create(); return (System.nanoTime() - start) / 1_000_000.0; });
                    for (var result : executor.invokeAll(tasks)) samples.add(result.get());
                }
                samples.sort(Double::compareTo);
                reports.add(Map.of("products", count, "requests", samples.size(), "p50Ms", samples.get(14), "p95Ms", samples.get(28), "maxMs", samples.getLast(), "concurrency", concurrency));
            }
        }
        var output = Path.of("build/reports/catalog-benchmark.json"); Files.createDirectories(output.getParent());
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("measuredAt", Instant.now().toString(), "environment", "MockMvc + Testcontainers PostgreSQL; warm JVM; no browser/network latency", "samples", reports)));
        verifyNoInteractions(search, web, ai);
    }
}
