package com.decisionassistant;

import com.decisionassistant.ai.*;
import com.decisionassistant.guidance.*;
import com.decisionassistant.identity.*;
import com.decisionassistant.lookup.SerperSearchProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static com.decisionassistant.decision.DecisionTypes.TemplateKey.EARBUDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {"decision-assistant.search.enabled=true", "decision-assistant.search.api-key=test",
        "decision-assistant.web.enabled=true", "decision-assistant.web.api-key=test", "decision-assistant.web.poll-interval=3600000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DecisionApiIntegrationTest.TestSecurityConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DiscoveryApiIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mvc;
    @Autowired ApplicationUserRepository users;
    @Autowired DiscoveryWorker worker;
    @Autowired ProductDiscoveryRepository runs;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean SerperSearchProvider search;
    @MockitoBean FirecrawlWebEvidenceProvider web;
    @MockitoBean ProductDiscoverer discoverer;
    @MockitoBean AiProperties ai;
    @MockitoBean EvidenceWorkflowWorker evidenceWorker;
    final ObjectMapper mapper = new ObjectMapper();
    RequestPostProcessor owner;
    private static final String BODY = """
            {"templateKey":"EARBUDS","intentText":"서버에만 남길 메모",
             "discovery":{"templateVersion":"intake-v1","use":"출퇴근","maxBudget":200000,
             "searchConsent":true,"collectionConsent":true,"aiConsent":true,"sensitiveDataAcknowledged":true}}
            """;

    @BeforeEach void setup() {
        owner = user();
        when(ai.enabled()).thenReturn(true); when(ai.provider()).thenReturn("gemini");
        when(ai.model()).thenReturn("test"); when(ai.timeout()).thenReturn(Duration.ofSeconds(3));
        when(ai.usesGemini()).thenReturn(true);
        when(search.search(anyString(), eq(5))).thenReturn(results());
        when(web.scrapeProduct(anyString())).thenAnswer(call -> defaultPage(call.getArgument(0), 100000, 150000));
        when(discoverer.discover(any())).thenReturn(new AiContracts.AiResult<>(new DiscoveryProposal(List.of(
                product("Buds A", "100,000 KRW"), product("Buds B", "150,000 KRW"))), 100, 50));
    }
    private ProposedProduct product(String name, String price) {
        return new ProposedProduct(name, url(name), name, "가격과 사양을 비교하세요.",
                List.of(new ProposedFact("가격", price, url(name), name + " 가격 " + price + ".")));
    }
    private String url(String name) { return "https://qcystore.co.kr/product/" + name.replace(" ", "-") + "/" + (100 + name.charAt(name.length() - 1)) + "/"; }
    private List<WebSearchProvider.SearchResult> results() {
        return List.of(new WebSearchProvider.SearchResult("상품", url("Buds A"), "untrusted snippet"),
                new WebSearchProvider.SearchResult("상품", url("Buds B"), "untrusted snippet"));
    }
    private WebEvidenceProvider.Page defaultPage(String url, int a, int b) {
        var name = url.equals(url("Buds A")) ? "Buds A" : "Buds B";
        return page(url, name, name.endsWith("A") ? a : b, "");
    }
    private WebEvidenceProvider.Page page(String url, String name, int price, String facts) {
        var html = "<script type='application/ld+json'>" + mapper.writeValueAsString(Map.of("@type", "Product", "name", name,
                "sku", name, "url", url, "offers", Map.of("@type", "Offer", "priceCurrency", "KRW", "price", price))) + "</script>";
        return new WebEvidenceProvider.Page(name, name + " 가격 " + price + " KRW. " + facts, html, url);
    }
    private RequestPostProcessor user() {
        var principal = new AppUserPrincipal(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "Test");
        users.save(new ApplicationUser(principal.userId(), principal.email(), principal.email(), "Test", Instant.now()));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test void policyOfferSortingAndExecutionProgressArePersistedWithoutChangingExecutionIdentity() throws Exception {
        var id = create();
        var queued = runs.findById(UUID.fromString(id)).orElseThrow();
        var execution = queued.executionId(); var version = queued.version();
        when(discoverer.discover(any())).thenAnswer(call -> {
            var running = runs.findById(UUID.fromString(id)).orElseThrow();
            assertThat(running.executionId()).isEqualTo(execution);
            assertThat(running.version()).isGreaterThan(version);
            assertThat(running.stage()).isEqualTo("AI");
            return new AiContracts.AiResult<>(new DiscoveryProposal(List.of(product("Buds B", "150,000 KRW"), product("Buds A", "100,000 KRW"))), 10, 10);
        });
        worker.processNext();
        mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.discovery.policyVersion").value(OfficialStorePolicy.VERSION))
                .andExpect(jsonPath("$.discovery.executionId").value(execution.toString()))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].name").value("Buds A"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].officialOffer.price").value(100000))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].recommendationGroup").value("RECOMMENDED"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].sourceKind").value("OFFICIAL_STORE"));
    }

    @Test void unverifiedOfficialClaimAndMissingPriceReturnEmptyWithoutExtraSearchOrAi() throws Exception {
        when(search.search(anyString(), eq(5))).thenReturn(List.of(new WebSearchProvider.SearchResult("공식이라고 주장", "https://unverified.example/buds", "")));
        var id = create(); worker.processNext();
        mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.discovery.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.discovery.emptyReason").value("NO_OFFICIAL_RESULTS"));
        verifyNoInteractions(web, discoverer);
        when(search.search(anyString(), eq(5))).thenReturn(results());
        when(web.scrapeProduct(anyString())).thenReturn(new WebEvidenceProvider.Page("제품", "가격 미확인"));
        var missing = create(); worker.processNext();
        mvc.perform(get("/api/v1/decisions/" + missing).with(owner))
                .andExpect(jsonPath("$.discovery.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.discovery.emptyReason").value("NO_VERIFIED_OFFERS"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions.length()").value(0));
        verify(search, times(2)).search(anyString(), eq(5)); verify(web, times(2)).scrapeProduct(anyString()); verifyNoInteractions(discoverer);
    }

    @Test void emptySearchOnRefreshDoesNotClaimThatPreviousProductsWereExcluded() throws Exception {
        when(search.search(anyString(), eq(5))).thenReturn(List.of());
        var id = create(); worker.processNext();
        var before = runs.findById(UUID.fromString(id)).orElseThrow();
        mvc.perform(post("/api/v1/decisions/" + id + "/discovery/refresh").with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":" + before.version() + "}"))
                .andExpect(status().isOk());
        worker.processNext();
        mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.discovery.emptyReason").value("NO_OFFICIAL_RESULTS"))
                .andExpect(jsonPath("$.discovery.history[0].emptyReason").value("NO_OFFICIAL_RESULTS"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions.length()").value(0));
        verify(search, times(2)).search(anyString(), eq(5));
        verifyNoInteractions(web, discoverer);
    }

    @Test void legacyRecordsAreNotRetroactivelyCertified() throws Exception {
        var id = create(); worker.processNext();
        jdbc.update("UPDATE product_discoveries SET policy_version = NULL, execution_id = NULL WHERE decision_id = ?", UUID.fromString(id));
        jdbc.update("UPDATE candidate_suggestions SET official_offer = NULL, product_identity = NULL, url = 'https://youtu.be/legacy' WHERE decision_id = ?", UUID.fromString(id));
        mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.discovery.policyVersion").doesNotExist())
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].officialOffer").doesNotExist())
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].sourceKind").value("VIDEO"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].recommendationGroup").value("LEGACY"));
    }

    @Test void refreshExcludesAllPreviousProductsAndPreservesAcceptedInputAndHistory() throws Exception {
        var id = create(); worker.processNext();
        var initial = mapper.readTree(mvc.perform(get("/api/v1/decisions/" + id).with(owner)).andReturn().getResponse().getContentAsString());
        var a = initial.path("guidance").path("candidateSuggestions").get(0).path("id").asText();
        var b = initial.path("guidance").path("candidateSuggestions").get(1).path("id").asText();
        var accepted = mapper.readTree(mvc.perform(patch("/api/v1/decisions/" + id + "/candidate-suggestions/" + a).with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}")).andReturn().getResponse().getContentAsString());
        var optionId = accepted.path("options").get(0).path("id").asText();
        var draft = Map.of("expectedVersion", accepted.path("version").asLong(), "title", "직접 편집한 비교",
                "context", Map.of("intendedUse", "유지할 입력", "notes", "메모 유지", "currency", "KRW", "budget", 200000),
                "options", accepted.path("options"), "criteria", accepted.path("criteria"), "constraints", accepted.path("constraints"),
                "constraintResults", List.of(), "values", List.of(Map.of("optionId", optionId, "criterionId", accepted.path("criteria").get(0).path("id").asText(),
                        "numericValue", 77777, "certainty", "CONFIRMED", "origin", "USER", "verificationStatus", "CONFIRMED")));
        mvc.perform(put("/api/v1/decisions/" + id + "/draft").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(draft))).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/decisions/" + id + "/candidate-suggestions/" + b).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DISMISS\"}")).andExpect(status().isOk());
        var before = runs.findById(UUID.fromString(id)).orElseThrow();
        var pending = mapper.readTree(mvc.perform(post("/api/v1/decisions/" + id + "/discovery/refresh").with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":" + before.version() + "}"))
                .andExpect(jsonPath("$.discovery.generation").value(1)).andExpect(jsonPath("$.discovery.displayedGeneration").value(0))
                .andExpect(jsonPath("$.context.notes").value("메모 유지")).andReturn().getResponse().getContentAsString());
        assertThat(pending.path("discovery").path("executionId").asText()).isNotEqualTo(before.executionId().toString());
        assertThat(runs.findById(UUID.fromString(id)).orElseThrow().conditions()).isEqualTo(before.conditions());
        var next = List.of("Buds C", "Buds D", "Buds E", "Buds F");
        var results = new ArrayList<WebSearchProvider.SearchResult>(); results.add(results().getFirst());
        next.forEach(name -> results.add(new WebSearchProvider.SearchResult(name, url(name), "")));
        when(search.search(anyString(), eq(5))).thenReturn(results);
        when(web.scrapeProduct(anyString())).thenAnswer(call -> page(call.getArgument(0), java.net.URI.create(call.getArgument(0)).getPath().split("/")[2].replace("-", " "), 100000, ""));
        when(discoverer.discover(any())).thenReturn(new AiContracts.AiResult<>(new DiscoveryProposal(next.stream().map(name -> product(name, "100000 KRW")).toList()), 10, 10));
        worker.processNext();
        mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.options.length()").value(1)).andExpect(jsonPath("$.options[0].id").value(optionId))
                .andExpect(jsonPath("$.values[0].numericValue").value(77777)).andExpect(jsonPath("$.title").value("직접 편집한 비교"))
                .andExpect(jsonPath("$.discovery.displayedGeneration").value(1))
                .andExpect(jsonPath("$.guidance.candidateSuggestions.length()").value(4))
                .andExpect(jsonPath("$.discovery.history[0].candidates.length()").value(2))
                .andExpect(jsonPath("$.discovery.history[0].candidates[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.discovery.history[0].candidates[1].status").value("DISMISSED"));
        assertThat(jdbc.queryForObject("select count(*) from candidate_suggestions where decision_id = ?", Integer.class, UUID.fromString(id))).isEqualTo(6);
        verify(web, times(1)).scrapeProduct(url("Buds A"));
        verify(search).search(argThat(query -> query.contains("-\"Buds A\"") && query.contains("-\"Buds B\"") && !query.contains("메모 유지")), eq(5));
        verify(discoverer, times(2)).discover(any());
    }

    @Test void failedRefreshKeepsPreviousListAndRetryUsesSameGeneration() throws Exception {
        var id = create(); worker.processNext();
        var before = runs.findById(UUID.fromString(id)).orElseThrow();
        var path = "/api/v1/decisions/" + id + "/discovery/refresh";
        var body = "{\"expectedVersion\":" + before.version() + "}";
        mvc.perform(post(path).with(user()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(post(path).with(owner).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post(path).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        var queued = runs.findById(UUID.fromString(id)).orElseThrow();
        mvc.perform(post(path).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":" + queued.version() + "}")).andExpect(status().isConflict());
        when(search.search(anyString(), eq(5))).thenThrow(new com.decisionassistant.lookup.LookupException(429, "SEARCH_MONTHLY_LIMIT", "fixture"));
        worker.processNext();
        mvc.perform(get("/api/v1/decisions/" + id).with(owner)).andExpect(jsonPath("$.discovery.status").value("FAILED"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions.length()").value(2)).andExpect(jsonPath("$.discovery.displayedGeneration").value(0));
        var failed = runs.findById(UUID.fromString(id)).orElseThrow();
        mvc.perform(post("/api/v1/decisions/" + id + "/discovery/retry").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + failed.version() + "}")).andExpect(status().isOk()).andExpect(jsonPath("$.discovery.generation").value(1));
        doReturn(results()).when(search).search(anyString(), eq(5));
        worker.processNext();
        mvc.perform(get("/api/v1/decisions/" + id).with(owner)).andExpect(jsonPath("$.discovery.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.discovery.emptyReason").value("NO_NEW_PRODUCTS"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions.length()").value(2)).andExpect(jsonPath("$.discovery.displayedGeneration").value(0));
        verify(discoverer, times(1)).discover(any()); verify(web, times(2)).scrapeProduct(anyString());
    }

    @Test void concurrentRefreshRequestsCreateOneNewGeneration() throws Exception {
        var id = create(); worker.processNext();
        var version = runs.findById(UUID.fromString(id)).orElseThrow().version();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> refresh = () -> mvc.perform(post("/api/v1/decisions/" + id + "/discovery/refresh")
                    .with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":" + version + "}"))
                    .andReturn().getResponse().getStatus();
            var first = pool.submit(refresh); var second = pool.submit(refresh);
            assertThat(List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(runs.findById(UUID.fromString(id)).orElseThrow().generation()).isEqualTo(1);
        worker.processNext();
        verify(search, times(2)).search(anyString(), eq(5)); verify(discoverer, times(1)).discover(any());
    }

    @Test @Timeout(20) void lateAiCompletionCannotOverwriteANewerExecution() throws Exception {
        var id = create();
        var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        when(ai.timeout()).thenReturn(Duration.ofSeconds(10));
        when(discoverer.discover(any())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(8, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("새 실행을 만들지 못했습니다.");
            return new AiContracts.AiResult<>(new DiscoveryProposal(List.of(product("Buds A", "100000 KRW"))), 1, 1);
        });
        try (var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var future = pool.submit(worker::processNext);
            try {
                assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                // 중단 감지가 완료된 상황을 재현한다. 이전 AI 응답은 아직 실행 중이다.
                jdbc.update("UPDATE product_discoveries SET status = 'FAILED', version = version + 1 WHERE decision_id = ?", UUID.fromString(id));
                var interrupted = runs.findById(UUID.fromString(id)).orElseThrow();
                mvc.perform(post("/api/v1/decisions/" + id + "/discovery/retry").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + interrupted.version() + "}")).andExpect(status().isOk());
                var next = runs.findById(UUID.fromString(id)).orElseThrow();
                assertThat(next.executionId()).isNotEqualTo(interrupted.executionId());
                release.countDown(); future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                var untouched = runs.findById(UUID.fromString(id)).orElseThrow();
                assertThat(untouched.status()).isEqualTo(ProductDiscovery.Status.PENDING);
                assertThat(untouched.executionId()).isEqualTo(next.executionId());
                assertThat(jdbc.queryForObject("select count(*) from candidate_suggestions where decision_id = ?", Integer.class, UUID.fromString(id))).isZero();
            } finally { release.countDown(); }
        }
        worker.processNext();
        assertThat(runs.findById(UUID.fromString(id)).orElseThrow().status()).isEqualTo(ProductDiscovery.Status.SUCCEEDED);
    }
    private String create() throws Exception {
        return create(BODY);
    }
    private String create(String body) throws Exception {
        var json = mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.options.length()").value(0))
                .andExpect(jsonPath("$.discovery.status").value("PENDING")).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asText();
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> selectedUses() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("\"uses\":[]", List.of()),
                org.junit.jupiter.params.provider.Arguments.of("\"uses\":[\"운동\"]", List.of("운동")),
                org.junit.jupiter.params.provider.Arguments.of("\"uses\":[\"운동\",\"출퇴근\",\"운동\"]", List.of("출퇴근", "운동")),
                org.junit.jupiter.params.provider.Arguments.of("\"uses\":[\"음악\",\"통화\",\"운동\",\"출퇴근\"]", List.of("출퇴근", "운동", "통화", "음악")),
                org.junit.jupiter.params.provider.Arguments.of("\"use\":\"출퇴근\",\"uses\":[]", List.of()),
                org.junit.jupiter.params.provider.Arguments.of("\"use\":\"출퇴근\",\"uses\":[\"운동\"]", List.of("운동")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("selectedUses")
    void multipleUsesArePersistedAndConsideredInOneRequest(String fields, List<String> expected) throws Exception {
        var id = create(BODY.replace("\"use\":\"출퇴근\"", fields));
        var conditions = runs.findById(UUID.fromString(id)).orElseThrow().conditions();
        assertThat(conditions.uses()).containsExactlyElementsOf(expected);
        assertThat(conditions.use()).isNull();
        var response = mapper.readTree(mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(response.path("context").path("intendedUse").asText())
                .contains(expected.isEmpty() ? "용도 미정" : String.join(" · ", expected));
        worker.processNext();
        assertThat(runs.findById(UUID.fromString(id)).orElseThrow().status()).isEqualTo(ProductDiscovery.Status.SUCCEEDED);
        verify(search, times(1)).search(eq(OfficialStorePolicy.searchScope(EARBUDS) + " 무선 이어폰"), eq(5));
        verify(discoverer, times(1)).discover(argThat(input -> input.uses().equals(expected)));
    }

    @Test void rejectsUnknownBlankNullAndTooManyUsesBeforeExternalCalls() throws Exception {
        for (var values : List.of("[\"게임\"]", "[\"출퇴근\",\"게임\"]")) {
            mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.replace("\"use\":\"출퇴근\"", "\"uses\":" + values)))
                    .andExpect(status().isUnprocessableEntity());
        }
        for (var values : List.of("[null]", "[\"\"]", "[\"출퇴근\",\"운동\",\"통화\",\"음악\",\"출퇴근\"]")) {
            mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.replace("\"use\":\"출퇴근\"", "\"uses\":" + values)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(search, web, discoverer);
    }

    @Test void discoveryPersistsSourcesRequiresApprovalAndCanChooseWithoutEvaluation() throws Exception {
        var id = create(); worker.processNext();
        var json = mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.discovery.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.options.length()").value(0)).andExpect(jsonPath("$.values.length()").value(0))
                .andExpect(jsonPath("$.guidance.candidateSuggestions.length()").value(2))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].facts[0].verificationStatus").value("UNREVIEWED"))
                .andReturn().getResponse().getContentAsString();
        var suggestions = mapper.readTree(json).get("guidance").get("candidateSuggestions");
        for (var suggestion : suggestions) {
            var url = "/api/v1/decisions/" + id + "/candidate-suggestions/" + suggestion.get("id").asText();
            mvc.perform(patch(url).with(user()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(patch(url).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
                    .andExpect(status().isOk());
            mvc.perform(patch(url).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
                    .andExpect(status().isOk());
        }
        var saved = mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.options.length()").value(2)).andExpect(jsonPath("$.productFacts.length()").value(2))
                .andExpect(jsonPath("$.guidance.evidence.length()").value(0)).andExpect(jsonPath("$.values.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        var selected = mapper.readTree(saved).get("options").get(0).get("id").asText();
        mvc.perform(post("/api/v1/decisions/" + id + "/resolutions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"CHOSEN\",\"selectedOptionId\":\"" + selected + "\"}"))
                .andExpect(status().isCreated());
        verify(search, times(1)).search(eq(OfficialStorePolicy.searchScope(EARBUDS) + " 무선 이어폰"), eq(5));
        verify(web, times(2)).scrapeProduct(anyString()); verify(discoverer, times(1)).discover(any());
        verify(discoverer).discover(argThat(input -> input.maxBudget().compareTo(new java.math.BigDecimal("200000")) == 0));
    }

    @Test void consentAuthenticationCsrfAndTemplateValidationBlockExternalCalls() throws Exception {
        mvc.perform(post("/api/v1/decisions").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/decisions").with(owner).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        for (var flag : List.of("searchConsent", "collectionConsent", "aiConsent", "sensitiveDataAcknowledged")) {
            mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.replace("\"" + flag + "\":true", "\"" + flag + "\":false"))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("intake-v1", "unknown"))).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/intake-templates").with(owner)).andExpect(jsonPath("$.length()").value(5));
        verifyNoInteractions(search, web, discoverer);
    }

    @Test void failureRequiresExplicitVersionedRetryAndRetainsExistingResults() throws Exception {
        var id = create();
        // 기존 JSONB에는 uses 없이 단일 use만 저장돼 있다.
        jdbc.update("UPDATE product_discoveries SET conditions = (conditions - 'uses' - 'minimumRequirements') || '{\"use\":\"출퇴근\"}'::jsonb WHERE decision_id = ?", UUID.fromString(id));
        assertThat(runs.findById(UUID.fromString(id)).orElseThrow().conditions().uses()).isNull();
        when(web.scrapeProduct(anyString())).thenThrow(new IllegalStateException("provider failure"));
        worker.processNext(); worker.processNext();
        var run = runs.findById(UUID.fromString(id)).orElseThrow();
        assertThat(run.status()).isEqualTo(ProductDiscovery.Status.FAILED);
        verify(search, times(1)).search(anyString(), eq(5)); verifyNoInteractions(discoverer);
        var path = "/api/v1/decisions/" + id + "/discovery/retry";
        var body = "{\"expectedVersion\":" + run.version() + "}";
        mvc.perform(post(path).with(user()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(post(path).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post(path).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        doAnswer(call -> defaultPage(call.getArgument(0), 100000, 150000)).when(web).scrapeProduct(anyString());
        worker.processNext();
        assertThat(runs.findById(UUID.fromString(id)).orElseThrow().status()).isEqualTo(ProductDiscovery.Status.SUCCEEDED);
        verify(discoverer).discover(argThat(input -> input.uses().equals(List.of("출퇴근")) && input.minimumRequirements().isEmpty()));
    }

    @Test void concurrentWorkersClaimOnceAndPartialRetryKeepsAcceptedFacts() throws Exception {
        var id = create();
        when(search.search(anyString(), eq(5))).thenReturn(List.of(
                results().getFirst(), results().getLast(),
                new WebSearchProvider.SearchResult("실패", url("Buds C"), "")));
        when(web.scrapeProduct(url("Buds C"))).thenThrow(new IllegalStateException("failed"));
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(worker::processNext); var second = pool.submit(worker::processNext);
            first.get(10, java.util.concurrent.TimeUnit.SECONDS); second.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        verify(search, times(1)).search(anyString(), eq(5));
        var json = mapper.readTree(mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.discovery.status").value("PARTIAL")).andReturn().getResponse().getContentAsString());
        var candidateId = json.get("guidance").get("candidateSuggestions").get(0).get("id").asText();
        mvc.perform(patch("/api/v1/decisions/" + id + "/candidate-suggestions/" + candidateId).with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/decisions/" + id + "/discovery/retry").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + json.get("discovery").get("version").asLong() + "}"))
                .andExpect(status().isOk());
        when(search.search(anyString(), eq(5))).thenReturn(results());
        doAnswer(call -> defaultPage(call.getArgument(0), 90000, 140000)).when(web).scrapeProduct(anyString());
        when(discoverer.discover(any())).thenReturn(new AiContracts.AiResult<>(new DiscoveryProposal(List.of(
                product("Buds A", "90,000 KRW"), product("Buds B", "140,000 KRW"))), 100, 50));
        worker.processNext();
        mvc.perform(get("/api/v1/decisions/" + id).with(owner)).andExpect(jsonPath("$.discovery.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.options.length()").value(1))
                .andExpect(jsonPath("$.productFacts[0].facts[0].value").value("100000 KRW"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions.length()").value(1))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].facts[0].value").value("140000 KRW"));
    }

    @Test void minimumRequirementsPersistAndRemainAdvisoryWithBoundedExternalCalls() throws Exception {
        var text = "Buds A ANC 지원. Buds B ANC 미지원. Buds C 제품 소개.";
        when(search.search(anyString(), eq(5))).thenReturn(java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> new WebSearchProvider.SearchResult("상품", url("Buds " + (char) ('A' + index)), "")).toList());
        when(web.scrapeProduct(anyString())).thenAnswer(call -> page(call.getArgument(0),
                java.net.URI.create(call.getArgument(0)).getPath().split("/")[2].replace("-", " "), 100000, text));
        when(discoverer.discover(any())).thenReturn(new AiContracts.AiResult<>(new DiscoveryProposal(List.of(
                new ProposedProduct("Buds A", url("Buds A"), "Buds A", "근거 확인", List.of(new ProposedFact("ANC 지원", "지원", url("Buds A"), "Buds A ANC 지원."))),
                new ProposedProduct("Buds B", url("Buds B"), "Buds B", "근거 확인", List.of(new ProposedFact("ANC 지원", "미지원", url("Buds B"), "Buds B ANC 미지원."))),
                new ProposedProduct("Buds C", url("Buds C"), "Buds C", "근거 확인", List.of()))), 100, 50));
        var body = BODY.replace("intake-v1", "intake-v2").replace("\"use\":\"출퇴근\"", "\"uses\":[\"출퇴근\"],\"minimumRequirements\":{\"anc\":\"required\"}");
        var id = create(body);
        assertThat(runs.findById(UUID.fromString(id)).orElseThrow().conditions().minimumRequirements()).containsEntry("anc", "required");
        worker.processNext(); worker.processNext();
        var response = mapper.readTree(mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.discovery.conditions.minimumRequirements.anc").value("required"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].requirementChecks[0].status").value("MEETS"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[1].requirementChecks[0].status").value("BELOW"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[2].requirementChecks[0].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.values.length()").value(0)).andReturn().getResponse().getContentAsString());
        var belowId = response.path("guidance").path("candidateSuggestions").get(1).path("id").asText();
        mvc.perform(get("/api/v1/decisions/" + id).with(user())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/decisions/" + id + "/candidate-suggestions/" + belowId).with(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productFacts[0].requirementChecks[0].status").value("BELOW"))
                .andExpect(jsonPath("$.productFacts[0].requirementChecks[0].facts[0].verificationStatus").value("UNREVIEWED"));
        mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.productFacts[0].requirementChecks[0].status").value("BELOW"));
        verify(search, times(1)).search(eq(OfficialStorePolicy.searchScope(EARBUDS) + " 무선 이어폰 ANC"), eq(5));
        verify(web, times(5)).scrapeProduct(anyString());
        verify(discoverer, times(1)).discover(argThat(input -> input.minimumRequirements().equals(List.of(new MinimumCondition("anc", "ANC 지원", "지원 필수")))
                && input.pages().size() == 5 && !mapper.writeValueAsString(input).contains("서버에만")));
    }

    @Test void invalidMinimumRequirementsAndLegacyVersionCannotTriggerProviders() throws Exception {
        for (var selection : List.of("{\"memory\":\"16\"}", "{\"anc\":\"yes\"}", "{\"unknown\":\"required\"}")) {
            mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.replace("intake-v1", "intake-v2").replace("\"use\":\"출퇴근\"", "\"minimumRequirements\":" + selection)))
                    .andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"use\":\"출퇴근\"", "\"minimumRequirements\":{\"anc\":\"required\"}")))
                .andExpect(status().isUnprocessableEntity());
        for (var selection : List.of("{\"anc\":null}", "{\"anc\":\"\"}", "{\"\":\"required\"}", "{\"a\":\"1\",\"b\":\"1\",\"c\":\"1\",\"d\":\"1\"}")) {
            mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.replace("intake-v1", "intake-v2").replace("\"use\":\"출퇴근\"", "\"minimumRequirements\":" + selection)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(search, web, discoverer);
    }

    @Test void confirmedNumericEditUpdatesDisplayedFactsAndRequirementChecksTogether() throws Exception {
        var body = BODY.replace("EARBUDS", "LAPTOP").replace("intake-v1", "intake-v2")
                .replace("\"use\":\"출퇴근\"", "\"uses\":[],\"minimumRequirements\":{\"memory\":\"16\",\"storage\":\"1000\"}");
        var source = url("Laptop A");
        when(search.search(anyString(), eq(5))).thenReturn(List.of(new WebSearchProvider.SearchResult("노트북", source, "")));
        when(web.scrapeProduct(source)).thenReturn(page(source, "Laptop A", 150000, "Laptop A 메모리 8GB. Laptop A 저장공간 256GB."));
        when(discoverer.discover(any())).thenReturn(new AiContracts.AiResult<>(new DiscoveryProposal(List.of(new ProposedProduct(
                "Laptop A", source, "Laptop A", "구성을 확인하세요", List.of(
                        new ProposedFact("메모리", "8GB", source, "Laptop A 메모리 8GB."),
                        new ProposedFact("저장공간", "256GB", source, "Laptop A 저장공간 256GB."))))), 10, 10));
        var id = create(body); worker.processNext();
        var json = mapper.readTree(mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.context.intendedUse").value(org.hamcrest.Matchers.containsString("메모리 16GB 이상")))
                .andReturn().getResponse().getContentAsString());
        var candidate = json.path("guidance").path("candidateSuggestions").get(0).path("id").asText();
        var accepted = mapper.readTree(mvc.perform(patch("/api/v1/decisions/" + id + "/candidate-suggestions/" + candidate)
                .with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
                .andExpect(jsonPath("$.productFacts[0].requirementChecks[0].status").value("BELOW"))
                .andReturn().getResponse().getContentAsString());
        var values = new ArrayList<Map<String, Object>>();
        for (var criterion : accepted.path("criteria")) {
            var name = criterion.path("name").asText();
            if (name.equals("메모리") || name.equals("스토리지")) values.add(Map.of(
                    "optionId", accepted.path("options").get(0).path("id").asText(), "criterionId", criterion.path("id").asText(),
                    "numericValue", name.equals("메모리") ? 32 : 1024, "certainty", "CONFIRMED", "origin", "USER", "verificationStatus", "CONFIRMED"));
        }
        var draft = Map.of("expectedVersion", accepted.path("version").asLong(), "title", accepted.path("title").asText(),
                "context", accepted.path("context"), "options", accepted.path("options"), "criteria", accepted.path("criteria"),
                "constraints", accepted.path("constraints"), "constraintResults", List.of(), "values", values);
        mvc.perform(put("/api/v1/decisions/" + id + "/draft").with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(draft)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productFacts[0].requirementChecks[0].status").value("MEETS"))
                .andExpect(jsonPath("$.productFacts[0].requirementChecks[1].status").value("MEETS"))
                .andExpect(jsonPath("$.productFacts[0].requirementChecks[1].facts[0].value").value("1024 GB"))
                .andExpect(jsonPath("$.productFacts[0].facts[?(@.name == '저장공간')].value").value(org.hamcrest.Matchers.hasItem("1024 GB")));
        mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.productFacts[0].requirementChecks[0].facts[0].verificationStatus").value("CONFIRMED"));
        verify(search, times(1)).search(anyString(), eq(5));
        verify(discoverer, times(1)).discover(any());
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> failureScenarios() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("quota", "SEARCH", "SEARCH_MONTHLY_LIMIT", "이번 달 제품 검색 한도"),
                org.junit.jupiter.params.provider.Arguments.of("noResults", "SEARCH", "SEARCH_NO_RESULTS", "공개 제품 페이지를 찾지 못했습니다"),
                org.junit.jupiter.params.provider.Arguments.of("collectionJson", "COLLECTION", "COLLECTION_RESPONSE_INVALID", "공개 페이지 수집 응답을 처리하지 못했습니다"),
                org.junit.jupiter.params.provider.Arguments.of("emptyPage", "COLLECTION", "COLLECTION_EMPTY", "비교할 내용이 없습니다"),
                org.junit.jupiter.params.provider.Arguments.of("aiJson", "AI", "AI_RESPONSE_INVALID", "AI 응답을 제품 정보로 변환하지 못했습니다"),
                org.junit.jupiter.params.provider.Arguments.of("aiTimeout", "AI", "AI_TIMEOUT", "시간이 초과되었습니다"),
                org.junit.jupiter.params.provider.Arguments.of("aiFailed", "AI", "AI_FAILED", "AI 제품 정보 정리에 실패했습니다"),
                org.junit.jupiter.params.provider.Arguments.of("noEvidence", "VALIDATION", "EVIDENCE_INSUFFICIENT", "제품을 확인할 근거가 부족합니다"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("failureScenarios")
    @ExtendWith(OutputCaptureExtension.class)
    void failureStageSurvivesWithoutLeakingProviderDetailsOrRetrying(String scenario, String stage, String code,
                                                                   String message, CapturedOutput output) throws Exception {
        var secret = "fixture-private-provider-body";
        switch (scenario) {
            case "quota" -> when(search.search(anyString(), eq(5))).thenThrow(new com.decisionassistant.lookup.LookupException(429, "SEARCH_MONTHLY_LIMIT", secret));
            case "noResults" -> when(search.search(anyString(), eq(5))).thenReturn(List.of());
            case "collectionJson" -> when(web.scrapeProduct(anyString())).thenThrow(new org.springframework.http.converter.HttpMessageConversionException(secret, new IllegalArgumentException(secret)));
            case "emptyPage" -> when(web.scrapeProduct(anyString())).thenReturn(new WebEvidenceProvider.Page("empty", " "));
            case "aiJson" -> when(discoverer.discover(any())).thenThrow(new org.springframework.http.converter.HttpMessageConversionException(secret));
            case "aiTimeout" -> when(discoverer.discover(any())).thenThrow(new AiInvocationException(secret, new java.util.concurrent.TimeoutException(secret)));
            case "aiFailed" -> when(discoverer.discover(any())).thenThrow(new IllegalStateException(secret));
            case "noEvidence" -> when(discoverer.discover(any())).thenReturn(new AiContracts.AiResult<>(new DiscoveryProposal(List.of()), 10, 10));
            default -> throw new AssertionError(scenario);
        }
        var id = create(); worker.processNext(); worker.processNext();
        var run = runs.findById(UUID.fromString(id)).orElseThrow();
        if (scenario.equals("noResults")) {
            assertThat(run.status()).isEqualTo(ProductDiscovery.Status.SUCCEEDED);
            assertThat(run.emptyReason()).isEqualTo("NO_OFFICIAL_RESULTS");
            assertThat(run.error()).isNull();
            mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                    .andExpect(jsonPath("$.discovery.emptyReason").value("NO_OFFICIAL_RESULTS"))
                    .andExpect(jsonPath("$.guidance.candidateSuggestions.length()").value(0));
            verify(search, times(1)).search(anyString(), eq(5)); verifyNoInteractions(web, discoverer);
            assertThat(output.getAll()).doesNotContain(secret);
            return;
        }
        assertThat(run.status()).isEqualTo(ProductDiscovery.Status.FAILED);
        assertThat(run.error()).contains(message).doesNotContain(secret);
        mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.discovery.error").value(org.hamcrest.Matchers.containsString(message)))
                .andExpect(jsonPath("$.guidance.candidateSuggestions.length()").value(0));
        assertThat(output.getAll()).contains("product_event=discovery_failed", "stage=" + stage, "code=" + code).doesNotContain(secret);
        if (scenario.equals("collectionJson")) assertThat(output.getAll()).contains("product_event=discovery_page_failed", "root_cause=IllegalArgumentException");
        verify(search, times(1)).search(anyString(), eq(5));
        if (stage.equals("SEARCH")) verifyNoInteractions(web, discoverer);
        else {
            verify(web, times(2)).scrapeProduct(anyString());
            verify(discoverer, times(stage.equals("COLLECTION") ? 0 : 1)).discover(any());
        }
    }

    @Test @ExtendWith(OutputCaptureExtension.class)
    void conversionFailureKeepsSuccessfulPagesAndCandidates(CapturedOutput output) throws Exception {
        when(search.search(anyString(), eq(5))).thenReturn(List.of(
                new WebSearchProvider.SearchResult("오류", url("Buds C"), ""), results().getFirst(), results().getLast()));
        when(web.scrapeProduct(url("Buds C"))).thenThrow(new org.springframework.http.converter.HttpMessageConversionException("fixture-private-provider-body"));
        var id = create(); worker.processNext(); worker.processNext();
        mvc.perform(get("/api/v1/decisions/" + id).with(owner))
                .andExpect(jsonPath("$.discovery.status").value("PARTIAL"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions.length()").value(2));
        verify(discoverer, times(1)).discover(argThat(input -> input.pages().size() == 2));
        verify(search, times(1)).search(anyString(), eq(5)); verify(web, times(3)).scrapeProduct(anyString());
        assertThat(output.getAll()).contains("product_event=discovery_page_failed", "code=COLLECTION_RESPONSE_INVALID")
                .doesNotContain("fixture-private-provider-body");
    }
}
