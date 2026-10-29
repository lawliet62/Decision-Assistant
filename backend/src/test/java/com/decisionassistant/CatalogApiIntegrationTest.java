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
    @Autowired CatalogProperties catalogProperties;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean QcySpecificationSource specificationSource;
    @MockitoBean AppleSpecificationSource appleSpecificationSource;
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
        jdbc.update("delete from catalog_observations"); jdbc.update("delete from catalog_spec_attempts"); jdbc.update("delete from catalog_products"); jdbc.update("delete from catalog_monthly_usage");
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
    @Test void storedFactReviewIsLoadedWithoutChangingManifestFacts() throws Exception {
        var p = product(1, Instant.now());
        var original = p.facts().get(1).evidence().fetchedAt();
        var reviewed = original.plusSeconds(10);
        jdbc.update("update catalog_products set fact_reviews = cast(? as jsonb) where id = ?",
                json.writeValueAsString(Map.of("호환성", reviewed.toString())), p.id());
        var loaded = products.findById(p.id()).orElseThrow();
        assertThat(loaded.facts().get(1).evidence().fetchedAt()).isEqualTo(original);
        assertThat(loaded.usableFacts(reviewed.plusSeconds(1)).get(1).fetchedAt()).isEqualTo(reviewed);
    }
    @Test void scheduledSpecificationReviewPersistsOnlyMatchingFactsAndRespectsAttemptInterval() throws Exception {
        var id = "qcy-t13-pro"; var now = Instant.now().minus(Duration.ofDays(31));
        var sourceUrl = "https://www.qcy.com/products/qcy-t13-pro";
        var p = new CatalogProduct(id);
        p.register(1, "QCY T13 Pro", url(9), "qcy:test", List.of(), true);
        products.saveAndFlush(p);
        var specWorker = new QcySpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager);
        specWorker.refresh();
        verify(specificationSource, never()).fetch();
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=?", Integer.class, id)).isZero();
        p = products.findById(id).orElseThrow();
        p.register(2, "QCY T13 Pro", url(9), "qcy:test", List.of(
                new CatalogProduct.Fact(new ProductFact("방수", "IPX5", sourceUrl, "Water Resistance Range: IPX5", now, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        specWorker.refresh();
        verify(specificationSource, never()).fetch();
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=?", Integer.class, id)).isZero();
        p = products.findById(id).orElseThrow();
        p.register(3, "QCY T13 Pro", url(9), "qcy:test", List.of(
                new CatalogProduct.Fact(new ProductFact("호환성", "iOS / Android", sourceUrl, "Compatible Systems: iOS / Android", now, "UNREVIEWED"), CatalogProduct.ReviewCycle.SOFTWARE),
                new CatalogProduct.Fact(new ProductFact("방수", "IPX4", sourceUrl, "Water Resistance Range: IPX4", now, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE),
                new CatalogProduct.Fact(new ProductFact("ANC 지원", "지원", sourceUrl, "50dB Adaptive ANC", now, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(specificationSource.fetch()).thenReturn("<title>QCY T13 Pro – QCY Official</title><meta name='description' content='50dB Adaptive ANC Up to 47H Playtime'><div class='specification-content'><div class='spec-details'><p>Product Name: QCY T13 Pro</p><p>Compatible Systems: iOS / Android<br>Water Resistance Range: IPX5</p></div></div>");
        specWorker.refresh(); specWorker.refresh();
        var facts = products.findById(id).orElseThrow().usableFacts(Instant.now());
        assertThat(facts).extracting(ProductFact::name).containsExactly("호환성", "방수", "ANC 지원");
        assertThat(facts.getFirst().fetchedAt()).isAfter(now);
        assertThat(facts.get(1).fetchedAt()).isEqualTo(now);
        assertThat(facts.get(2).fetchedAt()).isAfter(now);
        verify(specificationSource, times(1)).fetch();
    }
    @Test void ht19ReviewRequiresExactSourceAndPersistsOnlyMatchingWaterResistance() throws Exception {
        var id = "qcy-ht19"; var old = Instant.now().minus(Duration.ofDays(91));
        var source = "https://www.qcy.com/products/qcy-melobuds-n60";
        var p = new CatalogProduct(id);
        p.register(1, "QCY HT19", url(8), "qcy:test", List.of(), true);
        products.saveAndFlush(p);
        var worker = new Ht19SpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager);
        worker.refresh();
        verify(specificationSource, never()).fetchN60();
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=?", Integer.class, id)).isZero();
        p.register(2, "QCY HT19", url(8), "qcy:test", List.of(
                new CatalogProduct.Fact(new ProductFact("ANC 지원", "지원", source, "Bluetooth 6.0, 56dB ANC, Hi-Res, 45-Hour Playtime, HT19", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE),
                new CatalogProduct.Fact(new ProductFact("방수", "IPX5", source, "Water Resistance Range: IPX5", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(specificationSource.fetchN60()).thenReturn("<title>QCY MeloBuds N60: Affordable Wireless Earbuds with Rich Sound – QCY Official</title>"
                + "<p class='product__text subtitle'>Bluetooth 6.0, 56dB ANC, Hi-Res, 45-Hour Playtime, HT19</p>"
                + "<div class='specification-content'><div class='spec-details'><p>Product Name: QCY MeloBuds N60<br>Water Resistance Range: IPX4</p></div></div>");
        worker.refresh(); worker.refresh();
        var facts = products.findById(id).orElseThrow().usableFacts(Instant.now());
        assertThat(facts.getFirst().fetchedAt()).isAfter(old);
        assertThat(facts.get(1).fetchedAt()).isEqualTo(old);
        verify(specificationSource, times(1)).fetchN60();
    }
    @Test void appleReviewUpdatesOnlyMatchingSpecification() throws Exception {
        var id = "apple-airpods-pro-3"; var now = Instant.now().minus(Duration.ofDays(91));
        var source = "https://www.apple.com/kr/airpods-pro/specs/";
        var p = new CatalogProduct(id);
        p.register(1, "AirPods Pro 3", "https://www.apple.com/kr/shop/buy-airpods/airpods-pro-3", "apple:test", List.of(), true);
        products.saveAndFlush(p);
        var worker = new AppleSpecificationRefreshWorker(true, catalogProperties, products, jdbc, appleSpecificationSource, transactionManager);
        worker.refresh();
        verify(appleSpecificationSource, never()).fetch();
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=?", Integer.class, id)).isZero();
        p = products.findById(id).orElseThrow();
        p.register(2, "AirPods Pro 3", "https://www.apple.com/kr/shop/buy-airpods/airpods-pro-3", "apple:test", List.of(
                new CatalogProduct.Fact(new ProductFact("ANC 지원", "지원", "https://www.apple.com/kr/other/", "액티브 노이즈 캔슬링", now, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        worker.refresh();
        verify(appleSpecificationSource, never()).fetch();
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=?", Integer.class, id)).isZero();
        p = products.findById(id).orElseThrow();
        p.register(3, "AirPods Pro 3", "https://www.apple.com/kr/shop/buy-airpods/airpods-pro-3", "apple:test", List.of(
                new CatalogProduct.Fact(new ProductFact("ANC 지원", "지원", source, "액티브 노이즈 캔슬링", now, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE),
                new CatalogProduct.Fact(new ProductFact("방수", "IP57", source, "먼지, 땀, 습기에 강한 방진 및 생활 방수 디자인(IP57)", now, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(appleSpecificationSource.fetch()).thenReturn("<title>AirPods Pro 3 - 제품 사양 - Apple (KR)</title><div class='techspecs-section section-audio'><ul class='techspecs-list'><li>액티브 노이즈 캔슬링</li></ul></div><div class='techspecs-section section-water'><ul class='techspecs-list'><li>IP54</li></ul></div>");
        worker.refresh(); worker.refresh();
        var facts = products.findById(id).orElseThrow().usableFacts(Instant.now());
        assertThat(facts.getFirst().fetchedAt()).isAfter(now);
        assertThat(facts.get(1).fetchedAt()).isEqualTo(now);
        verify(appleSpecificationSource, times(1)).fetch();
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
