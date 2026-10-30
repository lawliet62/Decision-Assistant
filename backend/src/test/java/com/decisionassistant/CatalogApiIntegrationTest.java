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
    @MockitoBean MarshallSpecificationSource marshallSpecificationSource;
    @MockitoBean SennheiserSpecificationSource sennheiserSpecificationSource;
    @MockitoBean AnkerP31iSpecificationSource ankerSpecificationSource;
    @MockitoBean AnkerAeroclipSpecificationSource ankerAeroclipSpecificationSource;
    @MockitoBean AnkerLibertySpecificationSource ankerLibertySpecificationSource;
    @MockitoBean AnkerLibertyProSpecificationSource ankerLibertyProSpecificationSource;
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
    @Test void ht16ReviewPersistsOnlyExactOfficialModel() throws Exception {
        var id = "qcy-ht16"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://qcystore.co.kr/product/detail.html?product_no=990";
        var name = "QCY HT16 멜로버즈 무선 블루투스 이어폰 ANC 노이즈캔슬링 블랙";
        var p = new CatalogProduct(id);
        p.register(1, name, url, "qcy:test", List.of(new CatalogProduct.Fact(
                new ProductFact("ANC 지원", "지원", url, "ANC 노이즈캔슬링", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(specificationSource.fetchHt16()).thenReturn("<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/990/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_990\"}</script>");
        var worker = new Ht16SpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager);
        worker.refresh(); worker.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        verify(specificationSource, times(1)).fetchHt16();
    }
    @Test void t17ReviewPersistsOnlyExactOfficialModel() throws Exception {
        var id = "qcy-t17-app"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://qcystore.co.kr/product/detail.html?product_no=563";
        var name = "QCY T17APP 블루투스이어폰 커널형 무선이어폰 저지연 큐씨와이 초경량 블루투스5.3버전 블랙";
        var p = new CatalogProduct(id);
        p.register(1, name, url, "qcy:test", List.of(new CatalogProduct.Fact(
                new ProductFact("형태", "커널형", url, "커널형", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(specificationSource.fetchT17App()).thenReturn("<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/563/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_563\"}</script>");
        var worker = new T17SpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager);
        worker.refresh(); worker.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        verify(specificationSource, times(1)).fetchT17App();
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
    @Test void airpods5ReviewPersistsOnlyMatchingSpecification() throws Exception {
        var id = "apple-airpods-5-wireless"; var old = Instant.now().minus(Duration.ofDays(91));
        var source = "https://www.apple.com/kr/airpods-5/specs/";
        var p = new CatalogProduct(id);
        p.register(1, "AirPods 5", "https://www.apple.com/kr/shop/buy-airpods/airpods-5/무선-충전-케이스-포함", "apple:test", List.of(
                new CatalogProduct.Fact(new ProductFact("ANC 지원", "지원", source, "액티브 노이즈 캔슬링", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE),
                new CatalogProduct.Fact(new ProductFact("방수", "IP57", source, "먼지, 땀, 습기에 강한 방진 및 생활 방수 디자인(IP57)", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(appleSpecificationSource.fetchAirpods5()).thenReturn("<title>AirPods 5 - 제품 사양 - Apple (KR)</title><div class='techspecs-section section-audio'><ul class='techspecs-list'><li>액티브 노이즈 캔슬링</li></ul></div><div class='techspecs-section section-water'><ul class='techspecs-list'><li>IP54</li></ul></div>");
        var worker = new Apple5SpecificationRefreshWorker(true, catalogProperties, products, jdbc, appleSpecificationSource, transactionManager);
        worker.refresh(); worker.refresh();
        var facts = products.findById(id).orElseThrow().usableFacts(Instant.now());
        assertThat(facts.getFirst().fetchedAt()).isAfter(old);
        assertThat(facts.get(1).fetchedAt()).isEqualTo(old);
        verify(appleSpecificationSource, times(1)).fetchAirpods5();
    }
    @Test void marshallReviewRequiresMatchingModelAndPersistsOncePerWeek() throws Exception {
        var id = "marshall-motif-ii-anc"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://www.marshall.com/kr/ko/product/motif-ii-anc?pid=1006450";
        var p = new CatalogProduct(id);
        p.register(1, "MOTIF II A.N.C.", url, "marshall-kr:1006450", List.of(
                new CatalogProduct.Fact(new ProductFact("ANC 지원", "지원", url, "액티브 노이즈 캔슬링", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(marshallSpecificationSource.fetch()).thenReturn("<link rel='canonical' href='" + url + "'><h1>MOTIF II A.N.C.</h1>"
                + "<script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"MOTIF II A.N.C.\",\"sku\":\"1006450\",\"url\":\"" + url + "\"}</script>"
                + "<div><p>액티브 노이즈 캔슬링</p><div><p>예</p></div></div>");
        var worker = new MarshallSpecificationRefreshWorker(true, catalogProperties, products, jdbc, marshallSpecificationSource, transactionManager);
        worker.refresh(); worker.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        verify(marshallSpecificationSource, times(1)).fetch();
    }
    @Test void ct06ReviewPersistsAndReservesOncePerWeek() throws Exception {
        var id = "qcy-ct06"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://qcystore.co.kr/product/detail.html?product_no=994";
        var name = "QCY CT06 오픈형 귀찌 무선 블루투스 이어폰 러닝 자전거 운동용 이어커프";
        var p = new CatalogProduct(id);
        p.register(1, name, url, "qcy:test", List.of(
                new CatalogProduct.Fact(new ProductFact("형태", "오픈형", url, "오픈형", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(specificationSource.fetchCt06()).thenReturn("<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/994/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_994\"}</script>");
        var worker = new Ct06SpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager);
        worker.refresh(); worker.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=? and next_attempt_at > now() + interval '6 days'", Integer.class, id)).isEqualTo(1);
        verify(specificationSource, times(1)).fetchCt06();
    }
    @Test void ht08ReviewPersistsAndReservesOncePerWeek() throws Exception {
        var id = "qcy-ht08-pro-plus"; var old = Instant.now().minus(Duration.ofDays(91));
        var source = "https://qcystore.co.kr/article/%EB%A7%A4%EA%B1%B0%EC%A7%84/8/16170/";
        var name = "QCY HT08 멜로버즈 프로 플러스 블루투스 이어폰 노이즈캔슬링 / 블랙";
        var title = "QCY, 적응형 노이즈 캔슬링 블루투스 이어폰 ‘HT08 멜로버즈 프로 플러스’ 출시";
        var p = new CatalogProduct(id);
        p.register(1, name, "https://qcystore.co.kr/product/detail.html?product_no=977", "qcy:test", List.of(
                new CatalogProduct.Fact(new ProductFact("ANC 지원", "지원", source, "적응형 액티브 노이즈 캔슬링 기능", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(specificationSource.fetchHt08Article()).thenReturn("<title>" + title + " 도매특판 - QCY스토어</title>"
                + "<form id='BoardDelForm'><input id='no' name='no' value='16170'><div id='gd' class='xans-board-read'>"
                + "<div class='titleArea'><h2>" + title + "</h2></div><div class='detail'><div class='fr-view-article'>"
                + "QCY HT08 멜로버즈 프로 플러스는 적응형 액티브 노이즈 캔슬링 기능으로 작동한다.</div></div></div></form>");
        var worker = new Ht08SpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager);
        worker.refresh(); worker.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=? and next_attempt_at > now() + interval '6 days'", Integer.class, id)).isEqualTo(1);
        verify(specificationSource, times(1)).fetchHt08Article();
    }
    @Test void minorIvReviewPersistsAndReservesOncePerWeek() throws Exception {
        var id = "marshall-minor-iv-black"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://www.marshall.com/kr/ko/product/minor-iv?pid=1006653";
        var p = new CatalogProduct(id);
        p.register(1, "MINOR IV", url, "marshall-kr:1006653", List.of(
                new CatalogProduct.Fact(new ProductFact("방수", "IPX4", url, "이어버드 IPX4", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(marshallSpecificationSource.fetchMinorIv()).thenReturn("<link rel='canonical' href='" + url + "'><h1>MINOR IV</h1>"
                + "<script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"MINOR IV\",\"sku\":\"1006653\",\"url\":\"" + url + "\"}</script>"
                + "<div><div>방수</div><div>이어버드 IPX4</div></div>");
        var worker = new MinorIvSpecificationRefreshWorker(true, catalogProperties, products, jdbc, marshallSpecificationSource, transactionManager);
        worker.refresh(); worker.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=? and next_attempt_at > now() + interval '6 days'", Integer.class, id)).isEqualTo(1);
        verify(marshallSpecificationSource, times(1)).fetchMinorIv();
    }
    @Test void libertyProRefreshStoresDiscountAndWaterResistance() throws Exception {
        var id = "anker-liberty5-pro-d1203"; var url = "https://ankerkorea.co.kr/product/detail.html?product_no=154";
        var source = "https://www.ankerjapan.com/products/d1203"; var old = Instant.now().minus(Duration.ofDays(91));
        var name = "앤커 사운드코어 리버티 5 프로 무선 블루투스 이어폰 D1203";
        var p = new CatalogProduct(id);
        p.register(1, name, url, "anker-kr:154", List.of(new CatalogProduct.Fact(
                new ProductFact("방수", "IP55", source, "IP55", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        var colors = new String[]{"미드나잇 블랙", "펄 화이트", "스카이 블루", "실버 핑크"};
        var variants = new ArrayList<Map<String, Object>>(); var stocks = new LinkedHashMap<String, Object>();
        var options = new StringBuilder("<option value='*'>선택</option>");
        for (int i = 0; i < colors.length; i++) {
            var color = colors[i]; var code = "P00000FY000" + (char)('A' + i);
            variants.add(Map.of("@type", "Product", "name", name + " " + color, "sku", "cafe24_ankerkr_1_154_" + code,
                    "additionalProperty", List.of(Map.of("@type", "PropertyValue", "name", "색상선택", "value", color)),
                    "offers", Map.of("@type", "Offer", "url", "https://ankerkorea.co.kr/product/anker/154/?item_code=" + code,
                            "price", 249900, "priceCurrency", "KRW", "availability", "https://schema.org/InStock",
                            "priceValidUntil", LocalDate.now(ZoneId.of("Asia/Seoul")).plusYears(2) + " 00:00")));
            stocks.put(code, Map.of("option_value", color, "is_selling", "T", "is_display", "T", "is_auto_soldout", "F", "is_reserve_stat", "N",
                    "option_price", 249900, "stock_price", "0.00", "origin_option_added_price", "0.00"));
            options.append("<option value='").append(color).append("'>").append(color).append("</option>");
        }
        var group = json.writeValueAsString(Map.of("@type", "ProductGroup", "name", name, "productGroupID", "cafe24_ankerkr_1_154",
                "variesBy", List.of("색상선택"), "hasVariant", variants));
        var stock = json.writeValueAsString(stocks).replace("\\", "\\\\").replace("\"", "\\\"");
        var html = "<link rel='canonical' href='https://ankerkorea.co.kr/product/anker/154/'><script type='application/ld+json'>" + group
                + "</script><script>var option_stock_data = '" + stock + "';var product_sale_price = 214900;</script>"
                + "<div class='xans-product-detail'><table><tr><th>판매가</th><td><strong id='span_product_price_text'>249,900원</strong></td></tr>"
                + "<tr><th>할인가</th><td><span id='span_optimum_discount_price'>214,900원</span></td></tr></table>"
                + "<select product_type='product_option' option_product_no='154' option_title='색상선택'>" + options + "</select>"
                + "<div class='productAction'><a><span id='actionBuy'>구매하기</span></a></div></div>";
        when(web.scrapeProduct(url)).thenReturn(new WebEvidenceProvider.Page("", "", html, url));
        worker.refreshNext();
        assertThat(products.findById(id).orElseThrow().usableOffer().price()).isEqualByComparingTo("214900");
        when(ankerLibertyProSpecificationSource.fetch()).thenReturn("<title>Soundcore Liberty 5 Pro | ワイヤレスイヤホンの製品情報 | Anker Japan 公式オンラインストア</title>"
                + "<link rel='canonical' href='" + source + "'><h1>Soundcore Liberty 5 Pro</h1>"
                + "<div class='pfs-text'><h3>IP55 防塵・防水規格</h3><p>IP55の防塵・防水規格に対応</p></div>");
        var spec = new AnkerLibertyProSpecificationRefreshWorker(true, catalogProperties, products, jdbc, ankerLibertyProSpecificationSource, transactionManager);
        spec.refresh(); spec.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=? and next_attempt_at > now() + interval '6 days'", Integer.class, id)).isEqualTo(1);
        verify(ankerLibertyProSpecificationSource, times(1)).fetch();
    }
    @Test void libertyRefreshStoresDiscountAndAnc() throws Exception {
        var id = "anker-liberty5-a3957"; var url = "https://ankerkorea.co.kr/product/detail.html?product_no=40";
        var source = "https://www.soundcore.com/products/a3957-liberty-5-tws-earbuds"; var old = Instant.now().minus(Duration.ofDays(91));
        var name = "앤커 사운드코어 리버티5 노이즈캔슬링 블루투스 이어폰 A3957";
        var p = new CatalogProduct(id);
        p.register(1, name, url, "anker-kr:40", List.of(new CatalogProduct.Fact(
                new ProductFact("ANC 지원", "지원", source, "Real-Time Adaptive ANC 3.0", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        var colors = new String[]{"블랙", "화이트"};
        var variants = new ArrayList<Map<String, Object>>(); var stocks = new LinkedHashMap<String, Object>();
        var options = new StringBuilder("<option value='*'>선택</option>");
        for (int i = 0; i < colors.length; i++) {
            var color = colors[i]; var code = "P00000BO000" + (char)('B' + i);
            variants.add(Map.of("@type", "Product", "name", name + " " + color, "sku", "cafe24_ankerkr_1_40_" + code,
                    "additionalProperty", List.of(Map.of("@type", "PropertyValue", "name", "옵션선택", "value", color)),
                    "offers", Map.of("@type", "Offer", "url", "https://ankerkorea.co.kr/product/anker/40/?item_code=" + code,
                            "price", 139990, "priceCurrency", "KRW", "availability", "https://schema.org/InStock",
                            "priceValidUntil", LocalDate.now(ZoneId.of("Asia/Seoul")).plusYears(2) + " 00:00")));
            stocks.put(code, Map.of("option_value", color, "is_selling", "T", "is_display", "T", "is_auto_soldout", "F", "is_reserve_stat", "N",
                    "option_price", 139990, "stock_price", "0.00", "origin_option_added_price", "0.00"));
            options.append("<option value='").append(color).append("'>").append(color).append("</option>");
        }
        var group = json.writeValueAsString(Map.of("@type", "ProductGroup", "name", name, "productGroupID", "cafe24_ankerkr_1_40",
                "variesBy", List.of("옵션선택"), "hasVariant", variants));
        var stock = json.writeValueAsString(stocks).replace("\\", "\\\\").replace("\"", "\\\"");
        var html = "<link rel='canonical' href='https://ankerkorea.co.kr/product/anker/40/'><script type='application/ld+json'>" + group
                + "</script><script>var option_stock_data = '" + stock + "';var product_sale_price = 99900;</script>"
                + "<div class='xans-product-detail'><table><tr><th>판매가</th><td><strong id='span_product_price_text'>139,990원</strong></td></tr>"
                + "<tr><th>할인가</th><td><span id='span_optimum_discount_price'>99,900원</span></td></tr></table>"
                + "<select product_type='product_option' option_product_no='40' option_title='옵션선택'>" + options + "</select>"
                + "<div class='productAction'><a><span id='actionBuy'>구매하기</span></a></div></div>";
        when(web.scrapeProduct(url)).thenReturn(new WebEvidenceProvider.Page("", "", html, url));
        worker.refreshNext();
        assertThat(products.findById(id).orElseThrow().usableOffer().price()).isEqualByComparingTo("99900");
        when(ankerLibertySpecificationSource.fetch()).thenReturn("<title>soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby 3D Audio</title>"
                + "<link rel='canonical' href='" + source + "'><h1>soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby Audio</h1>"
                + "<meta name='description' content='Buy soundcore Liberty 5 earbuds with Dolby 3D Audio, adaptive ANC, and premium sound.'><strong>Real-Time Adaptive ANC 3.0:</strong>");
        var spec = new AnkerLibertySpecificationRefreshWorker(true, catalogProperties, products, jdbc, ankerLibertySpecificationSource, transactionManager);
        spec.refresh(); spec.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        verify(ankerLibertySpecificationSource, times(1)).fetch();
        var body = BODY.replace("\"maxBudget\":50000", "\"maxBudget\":100000").replace("\"compatibility\":\"Android\",", "");
        var result = mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var decision = json.readTree(result).path("id").asText();
        mvc.perform(get("/api/v1/decisions/" + decision).with(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].officialOffer.price").value(99900))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].recommendationGroup").value("RECOMMENDED"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].requirementChecks[0].status").value("MEETS"));
    }
    @Test void aeroclipRefreshStoresDiscountAndOpenForm() throws Exception {
        var id = "anker-aeroclip-a3388"; var url = "https://ankerkorea.co.kr/product/detail.html?product_no=45";
        var name = "앤커 사운드코어 에어로클립 오픈형 블루투스이어폰 A3388"; var old = Instant.now().minus(Duration.ofDays(91));
        var p = new CatalogProduct(id);
        p.register(1, name, url, "anker-kr:45", List.of(new CatalogProduct.Fact(
                new ProductFact("형태", "오픈형", url, "오픈형", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        var colors = new String[]{"블랙", "화이트", "핑크", "블루"};
        var variants = new ArrayList<Map<String, Object>>(); var stocks = new LinkedHashMap<String, Object>();
        var options = new StringBuilder("<option value='*'>선택</option>");
        for (int i = 0; i < colors.length; i++) {
            var color = colors[i]; var code = "P00000BT000" + (char)('B' + i);
            variants.add(Map.of("@type", "Product", "name", name + " " + color, "sku", "cafe24_ankerkr_1_45_" + code,
                    "additionalProperty", List.of(Map.of("@type", "PropertyValue", "name", "색상선택", "value", color)),
                    "offers", Map.of("@type", "Offer", "url", "https://ankerkorea.co.kr/product/anker/45/?item_code=" + code,
                            "price", 159990, "priceCurrency", "KRW", "availability", "https://schema.org/InStock",
                            "priceValidUntil", LocalDate.now(ZoneId.of("Asia/Seoul")).plusYears(2) + " 00:00")));
            stocks.put(code, Map.of("option_value", color, "is_selling", "T", "is_display", "T", "is_auto_soldout", "F", "is_reserve_stat", "N",
                    "option_price", 159990, "stock_price", "0.00", "origin_option_added_price", "0.00"));
            options.append("<option value='").append(color).append("'>").append(color).append("</option>");
        }
        var group = json.writeValueAsString(Map.of("@type", "ProductGroup", "name", name, "productGroupID", "cafe24_ankerkr_1_45",
                "variesBy", List.of("색상선택"), "hasVariant", variants));
        var stock = json.writeValueAsString(stocks).replace("\\", "\\\\").replace("\"", "\\\"");
        var html = "<link rel='canonical' href='https://ankerkorea.co.kr/product/anker/45/'><script type='application/ld+json'>" + group
                + "</script><script>var option_stock_data = '" + stock + "';var product_sale_price = 129900;</script>"
                + "<div class='xans-product-detail'><table><tr><th>판매가</th><td><strong id='span_product_price_text'>159,990원</strong></td></tr>"
                + "<tr><th>할인가</th><td><span id='span_optimum_discount_price'>129,900원</span></td></tr></table>"
                + "<select product_type='product_option' option_product_no='45' option_title='색상선택'>" + options + "</select>"
                + "<div class='productAction'><a><span id='actionBuy'>구매하기</span></a></div></div>";
        when(web.scrapeProduct(url)).thenReturn(new WebEvidenceProvider.Page("", "", html, url));
        worker.refreshNext();
        assertThat(products.findById(id).orElseThrow().usableOffer().price()).isEqualByComparingTo("129900");
        when(ankerAeroclipSpecificationSource.fetch()).thenReturn("<title>에어로클립 오픈형 러닝 블루투스이어폰 A3388</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://ankerkorea.co.kr/product/anker/45/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ankerkr_1_45\"}</script>");
        var spec = new AnkerAeroclipSpecificationRefreshWorker(true, catalogProperties, products, jdbc, ankerAeroclipSpecificationSource, transactionManager);
        spec.refresh(); spec.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        verify(ankerAeroclipSpecificationSource, times(1)).fetch();
    }
    @Test void ankerRefreshStoresDiscountAndMatchingAncEvidence() throws Exception {
        var id = "anker-p31i-d1202"; var url = "https://ankerkorea.co.kr/product/detail.html?product_no=43";
        var source = "https://www.ankerjapan.com/products/d1202"; var old = Instant.now().minus(Duration.ofDays(91));
        var name = "앤커 사운드코어 P31i(R60i NC) 노이즈캔슬링 무선 블루투스 이어폰 D1202";
        var p = new CatalogProduct(id);
        p.register(1, name, url, "anker-kr:43", List.of(new CatalogProduct.Fact(
                new ProductFact("ANC 지원", "지원", source, "アクティブノイズキャンセリング搭載", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        var colors = new String[]{"블랙", "블루", "핑크", "화이트"};
        var variants = new ArrayList<Map<String, Object>>(); var stocks = new LinkedHashMap<String, Object>();
        var options = new StringBuilder("<option value='*'>선택</option>");
        for (int i = 0; i < colors.length; i++) {
            var color = colors[i]; var code = "P00000BR000" + (char)('B' + i);
            variants.add(Map.of("@type", "Product", "name", name + " " + color, "sku", "cafe24_ankerkr_1_43_" + code,
                    "additionalProperty", List.of(Map.of("@type", "PropertyValue", "name", "색상선택", "value", color)),
                    "offers", Map.of("@type", "Offer", "url", "https://ankerkorea.co.kr/product/anker/43/?item_code=" + code,
                            "price", 59990, "priceCurrency", "KRW", "availability", "https://schema.org/InStock",
                            "priceValidUntil", LocalDate.now(ZoneId.of("Asia/Seoul")).plusYears(2) + " 00:00")));
            stocks.put(code, Map.of("option_value", color, "is_selling", "T", "is_display", "T", "is_auto_soldout", "F", "is_reserve_stat", "N",
                    "option_price", 59990, "stock_price", "0.00", "origin_option_added_price", "0.00"));
            options.append("<option value='").append(color).append("'>").append(color).append("</option>");
        }
        var group = json.writeValueAsString(Map.of("@type", "ProductGroup", "name", name, "productGroupID", "cafe24_ankerkr_1_43",
                "variesBy", List.of("색상선택"), "hasVariant", variants));
        var stock = json.writeValueAsString(stocks).replace("\\", "\\\\").replace("\"", "\\\"");
        var html = "<link rel='canonical' href='https://ankerkorea.co.kr/product/anker/43/'><script type='application/ld+json'>" + group
                + "</script><script>var option_stock_data = '" + stock + "';var product_sale_price = 39900;</script>"
                + "<div class='xans-product-detail'><table><tr><th>판매가</th><td><strong id='span_product_price_text'>59,990원</strong></td></tr>"
                + "<tr><th>할인가</th><td><span id='span_optimum_discount_price'>39,900원</span></td></tr></table>"
                + "<select product_type='product_option' option_product_no='43' option_title='색상선택'>" + options + "</select>"
                + "<div class='productAction'><a><span id='actionBuy'>구매하기</span></a></div></div>";
        when(web.scrapeProduct(url)).thenReturn(new WebEvidenceProvider.Page("", "", html, url));
        worker.refreshNext();
        assertThat(products.findById(id).orElseThrow().usableOffer().price()).isEqualByComparingTo("39900");
        when(ankerSpecificationSource.fetch()).thenReturn("<title>Soundcore P31i | 完全ワイヤレスイヤホンの製品情報 | Anker Japan 公式オンラインストア</title>"
                + "<link rel='canonical' href='" + source + "'><h1>Soundcore P31i</h1>"
                + "<meta name='description' content='Soundcore P31iならAnker (アンカー) Japan 公式オンラインストア。アクティブノイズキャンセリング搭載。'>");
        var spec = new AnkerP31iSpecificationRefreshWorker(true, catalogProperties, products, jdbc, ankerSpecificationSource, transactionManager);
        spec.refresh(); spec.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        verify(ankerSpecificationSource, times(1)).fetch();
        var body = BODY.replace("\"compatibility\":\"Android\",", "");
        var result = mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var decision = json.readTree(result).path("id").asText();
        mvc.perform(get("/api/v1/decisions/" + decision).with(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].officialOffer.price").value(39900))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].recommendationGroup").value("RECOMMENDED"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].requirementChecks[0].status").value("MEETS"));
    }
    @Test void bosePriceRefreshDoesNotInventAncEvidence() throws Exception {
        var id = "bose-qc-earbuds-ice-blue"; var url = "https://www.bose.co.kr/357/?idx=307";
        var name = "[New Launch] BOSE 보스 QC 이어버드(New)(아이스 블루)";
        var option = "O20260113696597912a514";
        var p = new CatalogProduct(id);
        p.register(1, name, url, "bose-kr:307", List.of(), true);
        products.saveAndFlush(p);
        var html = "<link rel='canonical' href='" + url + "'><script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"" + name
                + "\",\"brand\":{\"name\":\"BOSE\"},\"offers\":{\"@type\":\"Offer\",\"price\":229000,\"priceCurrency\":\"KRW\","
                + "\"url\":\"https://bose.co.kr/shop_view/?idx=307\",\"availability\":\"http://schema.org/InStock\"}}</script>"
                + "<div id='prod_goods_form'><h1 class='view_tit'>" + name + "</h1><div class='pay_detail'><span class='real_price'>229,000원</span></div>"
                + "<div id='prod_options'><label data-opttype='color' data-optcode='" + option + "' data-title='아이스 블루 (229,000원)'>"
                + "<input type='radio' value='" + option + "'></label></div><div class='buy_btns pc'><a class='_btn_buy' data-bs-prod-code='s2026011323a13db67b522'>구매하기</a></div></div>";
        when(web.scrapeProduct(url)).thenReturn(new WebEvidenceProvider.Page("", "", html, url));
        worker.refreshNext();
        assertThat(products.findById(id).orElseThrow().usableOffer().price()).isEqualByComparingTo("229000");
        assertThat(jdbc.queryForObject("select outcome from catalog_observations where product_id=?", String.class, id)).isEqualTo("VERIFIED");
        var body = BODY.replace("\"maxBudget\":50000", "\"maxBudget\":500000").replace("\"compatibility\":\"Android\",", "");
        var result = mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var decision = json.readTree(result).path("id").asText();
        mvc.perform(get("/api/v1/decisions/" + decision).with(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].priceStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].recommendationGroup").value("UNKNOWN"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].requirementChecks[0].status").value("UNKNOWN"));
    }
    @Test void sennheiserPriceRefreshPersistsValidatedOfferAndSupportsRecommendations() throws Exception {
        var id = "sennheiser-momentum-tw5";
        var url = "https://kr.sennheiser-hearing.com/product/detail.html?product_no=731";
        var canonical = "https://kr.sennheiser-hearing.com/product/모멘텀-트루-와이어리스-5/731/";
        var p = new CatalogProduct(id);
        p.register(1, "모멘텀 트루 와이어리스 5", url, "sennheiser-kr:cafe24_bnispace_1_731", List.of(
                new CatalogProduct.Fact(new ProductFact("ANC 지원", "지원", url, "하이브리드 적응형 ANC", Instant.now(), "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        var colors = new String[]{"블랙 그래파이트", "크림", "데님", "라벤더", "코퍼"};
        var variants = new ArrayList<Map<String, Object>>(); var options = new StringBuilder("<option value='*'>선택</option>");
        for (int i = 0; i < colors.length; i++) {
            var color = colors[i]; var code = "P0000BCD000" + (char)('B' + i);
            variants.add(Map.of("@type", "Product", "name", "모멘텀 트루 와이어리스 5 " + color, "color", color,
                    "sku", "cafe24_bnispace_1_731_" + code, "offers", Map.of("@type", "Offer", "price", 429000,
                    "priceCurrency", "KRW", "availability", "https://schema.org/InStock", "url", canonical + "?item_code=" + code)));
            options.append("<option value='").append(color).append("'>").append(color).append("</option>");
        }
        var data = json.writeValueAsString(Map.of("@type", "ProductGroup", "name", "모멘텀 트루 와이어리스 5",
                "productGroupID", "cafe24_bnispace_1_731", "variesBy", List.of("color"), "hasVariant", variants));
        var html = "<link rel='canonical' href='" + canonical + "'><script type='application/ld+json'>" + data + "</script>"
                + "<div class='xans-product-detail'><span class='senn-final-price'>₩429,000</span><select product_type='product_option' option_product_no='731' option_title='컬러'>"
                + options + "</select><div class='productAction'><a class='btnSubmit gFull' href='#none'><span id='actionBuy'>바로 구매하기</span></a></div></div>";
        when(web.scrapeProduct(url)).thenReturn(new WebEvidenceProvider.Page("", "", html, url));
        worker.refreshNext();
        assertThat(products.findById(id).orElseThrow().usableOffer().price()).isEqualByComparingTo("429000");
        assertThat(jdbc.queryForObject("select outcome from catalog_observations where product_id=?", String.class, id)).isEqualTo("VERIFIED");
        assertThat(jdbc.queryForObject("select calls from catalog_monthly_usage", Integer.class)).isEqualTo(1);
        var body = BODY.replace("\"maxBudget\":50000", "\"maxBudget\":500000").replace("\"compatibility\":\"Android\",", "");
        var result = mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var decision = json.readTree(result).path("id").asText();
        mvc.perform(get("/api/v1/decisions/" + decision).with(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.discovery.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].name").value("모멘텀 트루 와이어리스 5"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].officialOffer.price").value(429000))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].priceStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].recommendationGroup").value("RECOMMENDED"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].requirementChecks[0].status").value("MEETS"));
        var lowBudget = body.replace("\"maxBudget\":500000", "\"maxBudget\":400000");
        var lowResult = mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(lowBudget))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var lowDecision = json.readTree(lowResult).path("id").asText();
        mvc.perform(get("/api/v1/decisions/" + lowDecision).with(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].priceStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].recommendationGroup").value("BELOW"));
    }
    @Test void sennheiserReviewsMatchingFactAndKeepsChangedFactUnconfirmed() throws Exception {
        var id = "sennheiser-momentum-tw5"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://kr.sennheiser-hearing.com/product/detail.html?product_no=731";
        var p = new CatalogProduct(id);
        p.register(1, "모멘텀 트루 와이어리스 5", url, "sennheiser-kr:cafe24_bnispace_1_731", List.of(
                new CatalogProduct.Fact(new ProductFact("ANC 지원", "지원", url, "하이브리드 적응형 ANC", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE),
                new CatalogProduct.Fact(new ProductFact("방수", "IP54", url, "IP54 방진 및 생활 방수 (이어버드만 해당)", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(sennheiserSpecificationSource.fetch()).thenReturn("<link rel='canonical' href='https://kr.sennheiser-hearing.com/product/모멘텀-트루-와이어리스-5/731/'>"
                + "<h1>모멘텀 트루 와이어리스 5</h1><script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"모멘텀 트루 와이어리스 5\",\"productGroupID\":\"cafe24_bnispace_1_731\"}</script>"
                + "<div class='table-wrapper'><div class='table-row'><div class='label'>액티브 노이즈 캔슬링</div><div class='description'>하이브리드 적응형 ANC</div></div>"
                + "<div class='table-row'><div class='label'>방진·방수 등급</div><div class='description'>IP55</div></div></div>");
        var spec = new SennheiserSpecificationRefreshWorker(true, catalogProperties, products, jdbc, sennheiserSpecificationSource, transactionManager);
        spec.refresh(); spec.refresh();
        var facts = products.findById(id).orElseThrow().usableFacts(Instant.now());
        assertThat(facts.getFirst().fetchedAt()).isAfter(old);
        assertThat(facts.get(1).fetchedAt()).isEqualTo(old);
        verify(sennheiserSpecificationSource, times(1)).fetch();
    }
    @Test void ht10SpecificationRefreshSavesVerifiedAncOnce() throws Exception {
        var id = "qcy-ht10-hires"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://qcystore.co.kr/product/detail.html?product_no=924";
        var name = "QCY HT10 Hi-Res ANC APP 노이즈캔슬링 블루투스 이어폰 LDAC / 블랙";
        var p = new CatalogProduct(id);
        p.register(1, name, url, "qcy:test", List.of(new CatalogProduct.Fact(
                new ProductFact("ANC 지원", "지원", url, "ANC APP 노이즈캔슬링", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(specificationSource.fetchHt10()).thenReturn("<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/924/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_924\"}</script>");
        var worker = new Ht10SpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager);
        worker.refresh(); worker.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=?", Integer.class, id)).isEqualTo(1);
        verify(specificationSource, times(1)).fetchHt10();
    }
    @Test void t29SpecificationRefreshSavesVerifiedFormOnce() throws Exception {
        var id = "qcy-t29-app"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://qcystore.co.kr/product/detail.html?product_no=709";
        var name = "QCY T29 APP 블루투스이어폰 오픈형 13mm 드라이버";
        var p = new CatalogProduct(id);
        p.register(1, name, url, "qcy:test", List.of(new CatalogProduct.Fact(
                new ProductFact("형태", "오픈형", url, "오픈형", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(specificationSource.fetchT29()).thenReturn("<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/709/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_709\"}</script>");
        var worker = new T29SpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager);
        worker.refresh(); worker.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=?", Integer.class, id)).isEqualTo(1);
        verify(specificationSource, times(1)).fetchT29();
    }
    @Test void ht20SpecificationRefreshSavesVerifiedFormOnce() throws Exception {
        var id = "qcy-ht20"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://qcystore.co.kr/product/detail.html?product_no=1028";
        var name = "QCY HT20 멜로버즈 블루투스 무선 이어폰 노이즈캔슬링 AI마이크 C타입 커널형 게이밍";
        var p = new CatalogProduct(id);
        p.register(1, name, url, "qcy:test", List.of(new CatalogProduct.Fact(
                new ProductFact("형태", "커널형", url, "커널형", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(specificationSource.fetchHt20()).thenReturn("<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/1028/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_1028\"}</script>");
        var worker = new Ht20SpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager);
        worker.refresh(); worker.refresh();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isAfter(old);
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=?", Integer.class, id)).isEqualTo(1);
        verify(specificationSource, times(1)).fetchHt20();
    }
    @Test void concurrentSpecificationWorkersShareOneAttempt() throws Exception {
        var id = "qcy-ht16"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://qcystore.co.kr/product/detail.html?product_no=990";
        var p = new CatalogProduct(id);
        p.register(1, "HT16", url, "qcy:test", List.of(new CatalogProduct.Fact(
                new ProductFact("ANC 지원", "지원", url, "ANC 노이즈캔슬링", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(specificationSource.fetchHt16()).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
            return "";
        });
        var worker = new Ht16SpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var first = executor.submit(worker::refresh);
            try {
                assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                worker.refresh();
            } finally { release.countDown(); }
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        verify(specificationSource, times(1)).fetchHt16();
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=?", Integer.class, id)).isEqualTo(1);
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isEqualTo(old);
    }
    @Test void specificationResponseForPreviousRevisionCannotRefreshNewRevision() throws Exception {
        var id = "qcy-ht16"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://qcystore.co.kr/product/detail.html?product_no=990";
        var fact = new CatalogProduct.Fact(new ProductFact("ANC 지원", "지원", url, "ANC 노이즈캔슬링", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE);
        var p = new CatalogProduct(id); p.register(1, "HT16", url, "qcy:test", List.of(fact), true); products.saveAndFlush(p);
        when(specificationSource.fetchHt16()).thenAnswer(call -> {
            var changed = products.findById(id).orElseThrow();
            changed.register(2, "HT16", url, "qcy:test", List.of(fact), true); products.saveAndFlush(changed);
            var name = "QCY HT16 멜로버즈 무선 블루투스 이어폰 ANC 노이즈캔슬링 블랙";
            return "<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                    + "<link rel='canonical' href='https://qcystore.co.kr/product/model/990/'>"
                    + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_990\"}</script>";
        });
        new Ht16SpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager).refresh();
        var updated = products.findById(id).orElseThrow();
        assertThat(updated.revision()).isEqualTo(2);
        assertThat(updated.usableFacts(Instant.now()).getFirst().fetchedAt()).isEqualTo(old);
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=?", Integer.class, id)).isEqualTo(1);
    }
    @Test void failedSpecificationRequestKeepsPreviousReviewAndWeeklyLimit() throws Exception {
        var id = "qcy-ht16"; var old = Instant.now().minus(Duration.ofDays(91));
        var url = "https://qcystore.co.kr/product/detail.html?product_no=990";
        var p = new CatalogProduct(id); p.register(1, "HT16", url, "qcy:test", List.of(new CatalogProduct.Fact(
                new ProductFact("ANC 지원", "지원", url, "ANC 노이즈캔슬링", old, "UNREVIEWED"), CatalogProduct.ReviewCycle.HARDWARE)), true);
        products.saveAndFlush(p);
        when(specificationSource.fetchHt16()).thenThrow(new IllegalStateException("source unavailable"));
        var worker = new Ht16SpecificationRefreshWorker(true, catalogProperties, products, jdbc, specificationSource, transactionManager);
        worker.refresh(); worker.refresh();
        verify(specificationSource, times(1)).fetchHt16();
        assertThat(products.findById(id).orElseThrow().usableFacts(Instant.now()).getFirst().fetchedAt()).isEqualTo(old);
        assertThat(jdbc.queryForObject("select count(*) from catalog_spec_attempts where product_id=?", Integer.class, id)).isEqualTo(1);
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
