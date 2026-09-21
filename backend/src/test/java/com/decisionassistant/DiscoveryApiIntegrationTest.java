package com.decisionassistant;

import com.decisionassistant.ai.*;
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
        when(search.search(anyString(), eq(5))).thenReturn(List.of(new WebSearchProvider.SearchResult("비교", "https://example.com/compare", "untrusted snippet")));
        when(web.scrape("https://example.com/compare")).thenReturn(new WebEvidenceProvider.Page("비교", "Buds A 가격 100,000 KRW. Buds B 가격 150,000 KRW."));
        when(discoverer.discover(any())).thenReturn(new AiContracts.AiResult<>(new DiscoveryProposal(List.of(
                product("Buds A", "100,000 KRW"), product("Buds B", "150,000 KRW"))), 100, 50));
    }
    private ProposedProduct product(String name, String price) {
        return new ProposedProduct(name, "https://example.com/compare", name, "가격과 사양을 비교하세요.",
                List.of(new ProposedFact("가격", price, "https://example.com/compare", name + " 가격 " + price + ".")));
    }
    private RequestPostProcessor user() {
        var principal = new AppUserPrincipal(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "Test");
        users.save(new ApplicationUser(principal.userId(), principal.email(), principal.email(), "Test", Instant.now()));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
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
        verify(search, times(1)).search(argThat(query -> expected.stream().allMatch(query::contains)), eq(5));
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
        verify(search, times(1)).search(argThat(query -> query.contains("200000") && !query.contains("서버에만")), eq(5));
        verify(web, times(1)).scrape(anyString()); verify(discoverer, times(1)).discover(any());
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
        jdbc.update("UPDATE product_discoveries SET conditions = (conditions - 'uses') || '{\"use\":\"출퇴근\"}'::jsonb WHERE decision_id = ?", UUID.fromString(id));
        assertThat(runs.findById(UUID.fromString(id)).orElseThrow().conditions().uses()).isNull();
        when(web.scrape(anyString())).thenThrow(new IllegalStateException("provider failure"));
        worker.processNext(); worker.processNext();
        var run = runs.findById(UUID.fromString(id)).orElseThrow();
        assertThat(run.status()).isEqualTo(ProductDiscovery.Status.FAILED);
        verify(search, times(1)).search(anyString(), eq(5)); verifyNoInteractions(discoverer);
        var path = "/api/v1/decisions/" + id + "/discovery/retry";
        var body = "{\"expectedVersion\":" + run.version() + "}";
        mvc.perform(post(path).with(user()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(post(path).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post(path).with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        doReturn(new WebEvidenceProvider.Page("비교", "Buds A 가격 100,000 KRW. Buds B 가격 150,000 KRW.")).when(web).scrape(anyString());
        worker.processNext();
        assertThat(runs.findById(UUID.fromString(id)).orElseThrow().status()).isEqualTo(ProductDiscovery.Status.SUCCEEDED);
        verify(discoverer).discover(argThat(input -> input.uses().equals(List.of("출퇴근"))));
    }

    @Test void concurrentWorkersClaimOnceAndPartialRetryKeepsAcceptedFacts() throws Exception {
        var id = create();
        when(search.search(anyString(), eq(5))).thenReturn(List.of(
                new WebSearchProvider.SearchResult("비교", "https://example.com/compare", ""),
                new WebSearchProvider.SearchResult("실패", "https://example.com/failed", "")));
        when(web.scrape("https://example.com/failed")).thenThrow(new IllegalStateException("failed"));
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
        doReturn(new WebEvidenceProvider.Page("비교", "Buds A 가격 90,000 KRW. Buds B 가격 140,000 KRW.")).when(web).scrape(anyString());
        when(discoverer.discover(any())).thenReturn(new AiContracts.AiResult<>(new DiscoveryProposal(List.of(
                product("Buds A", "90,000 KRW"), product("Buds B", "140,000 KRW"))), 100, 50));
        worker.processNext();
        mvc.perform(get("/api/v1/decisions/" + id).with(owner)).andExpect(jsonPath("$.discovery.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.options.length()").value(1))
                .andExpect(jsonPath("$.productFacts[0].facts[0].value").value("100,000 KRW"))
                .andExpect(jsonPath("$.guidance.candidateSuggestions.length()").value(1))
                .andExpect(jsonPath("$.guidance.candidateSuggestions[0].facts[0].value").value("140,000 KRW"));
    }
}
