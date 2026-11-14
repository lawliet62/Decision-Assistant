package com.decisionassistant;

import com.decisionassistant.ai.AiContracts;
import com.decisionassistant.ai.AiProperties;
import com.decisionassistant.decision.Decision;
import com.decisionassistant.decision.DecisionRepository;
import com.decisionassistant.identity.AppUserPrincipal;
import com.decisionassistant.identity.ApplicationUser;
import com.decisionassistant.identity.ApplicationUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.decisionassistant.decision.DecisionTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DecisionApiIntegrationTest.TestSecurityConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AiDeletionIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DecisionRepository decisions;
    @Autowired ApplicationUserRepository users;
    @MockitoBean AiProperties ai;
    @MockitoBean AiContracts.DecisionStructurer structurer;
    @MockitoBean AiContracts.DecisionAssessor assessor;
    private final ObjectMapper mapper = new ObjectMapper();
    private RequestPostProcessor owner;
    private UUID decisionId;

    @BeforeEach
    void setup() {
        var userId = UUID.randomUUID();
        var email = userId + "@example.com";
        users.saveAndFlush(new ApplicationUser(userId, email, email, "AI 경합 테스트", Instant.now()));
        owner = authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AppUserPrincipal(userId, email, "AI 경합 테스트"), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        var decision = new Decision(userId, "삭제 경합", TemplateKey.CUSTOM, "fixture", Instant.now());
        var criterion = decision.addCriterion(null, "기준", CriterionKind.RATING_1_TO_5,
                PreferenceDirection.NOT_APPLICABLE, BigDecimal.ONE, null, null, null, true);
        for (var name : List.of("후보 A", "후보 B")) {
            var option = decision.addOption(null, name, null, null, null);
            decision.addValue(null, option.id(), criterion.id(), null, 3, ValueCertainty.CONFIRMED, null, null);
        }
        decisionId = decisions.saveAndFlush(decision).id();
        when(ai.enabled()).thenReturn(true);
        when(ai.provider()).thenReturn("gemini");
        when(ai.model()).thenReturn("fixture");
        when(ai.timeout()).thenReturn(Duration.ofSeconds(10));
        when(ai.usesGemini()).thenReturn(true);
    }

    @ParameterizedTest
    @CsvSource({
            "STRUCTURE,false,true", "STRUCTURE,true,true",
            "EVALUATION,false,true", "EVALUATION,true,true",
            "RETRY,false,true", "RETRY,true,true",
            "STRUCTURE,false,false", "STRUCTURE,true,false",
            "EVALUATION,false,false", "EVALUATION,true,false",
            "RETRY,false,false", "RETRY,true,false"
    })
    @Timeout(25)
    void completionWritesOnlyWhileParentIsActive(String kind, boolean providerFails, boolean deleted) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(structurer.structure(any())).thenAnswer(call -> {
            awaitProvider(entered, release, providerFails);
            return new AiContracts.AiResult<>(new AiContracts.StructureProposal(
                    List.of(), List.of(), List.of(), List.of(), List.of()), 1, 1);
        });
        when(assessor.assess(any())).thenAnswer(call -> {
            awaitProvider(entered, release, providerFails);
            return new AiContracts.AiResult<>(new AiContracts.LlmAssessment(
                    null, true, "판단 보류", List.of(), List.of(), List.of(), List.of()), 1, 1);
        });
        var endpoint = "/api/v1/decisions/" + decisionId;
        var table = "STRUCTURE".equals(kind) ? "structure_runs" : "evaluations";
        var stateColumn = "STRUCTURE".equals(kind) ? "status" : "llm_status";
        var body = "{\"expectedVersion\":0,\"aiConsent\":true,\"includeLlm\":true,\"sensitiveDataAcknowledged\":true}";
        String path;
        if ("STRUCTURE".equals(kind)) path = endpoint + "/structure-proposals";
        else if ("EVALUATION".equals(kind)) path = endpoint + "/evaluations";
        else {
            var prepared = mvc.perform(post(endpoint + "/evaluations").with(owner).with(csrf())
                    .contentType("application/json").content("{\"expectedVersion\":0,\"includeLlm\":false,\"aiConsent\":false}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            path = endpoint + "/evaluations/" + mapper.readTree(prepared).get("id").asText() + "/llm-retries";
        }
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> mvc.perform(post(path).with(owner).with(csrf())
                    .contentType("application/json").content(body)).andReturn().getResponse());
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var before = jdbc.queryForObject("select to_jsonb(t)::text from " + table + " t where decision_id = ?", String.class, decisionId);
                if (deleted) {
                    // 외부 호출 중 부모 잠금을 유지하면 이 삭제가 완료되지 못한다.
                    mvc.perform(delete(endpoint).param("expectedVersion", "0").with(owner).with(csrf()))
                            .andExpect(status().isNoContent());
                }
                release.countDown();
                var response = pending.get(5, TimeUnit.SECONDS);
                if (deleted) {
                    assertThat(response.getStatus()).isEqualTo(404);
                    assertThat(jdbc.queryForObject("select to_jsonb(t)::text from " + table + " t where decision_id = ?", String.class, decisionId))
                            .isEqualTo(before);
                    mvc.perform(patch(endpoint + "/retention").with(owner).with(csrf()).contentType("application/json")
                            .content("{\"expectedVersion\":1,\"action\":\"RESTORE\"}"))
                            .andExpect(status().isOk());
                    assertThat(jdbc.queryForObject("select " + stateColumn + " from " + table + " where decision_id = ?", String.class, decisionId))
                            .isEqualTo("PENDING");
                } else {
                    assertThat(response.getStatus()).isEqualTo("RETRY".equals(kind) ? 200 : 201);
                    assertThat(jdbc.queryForObject("select " + stateColumn + " from " + table + " where decision_id = ?", String.class, decisionId))
                            .isEqualTo(providerFails ? "FAILED" : "SUCCEEDED");
                }
            } finally { release.countDown(); }
        }
    }

    private void awaitProvider(CountDownLatch entered, CountDownLatch release, boolean providerFails) throws InterruptedException {
        entered.countDown();
        if (!release.await(8, TimeUnit.SECONDS)) throw new AssertionError("삭제 완료를 기다리지 못했습니다.");
        if (providerFails) throw new IllegalStateException("fixture provider failure");
    }
}
