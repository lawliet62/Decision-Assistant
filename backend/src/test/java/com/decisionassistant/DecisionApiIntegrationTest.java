package com.decisionassistant;

import com.decisionassistant.identity.AppUserPrincipal;
import com.decisionassistant.identity.ApplicationUser;
import com.decisionassistant.identity.ApplicationUserRepository;
import com.decisionassistant.identity.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DecisionApiIntegrationTest.TestSecurityConfiguration.class)
class DecisionApiIntegrationTest {

    static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final AppUserPrincipal TEST_USER = new AppUserPrincipal(
            TEST_USER_ID, "owner@example.com", "Owner");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationUserRepository users;

    tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();

    @BeforeEach
    void createTestUser() {
        users.findById(TEST_USER_ID).orElseGet(() -> users.save(new ApplicationUser(
                TEST_USER_ID, TEST_USER.email(), TEST_USER.email().toLowerCase(), TEST_USER.displayName(), Instant.now())));
    }

    @Test
    void bootsMigratesDatabaseAndCreatesLaptopDecision() throws Exception {
        var response = mvc.perform(post("/api/v1/decisions").with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"새 노트북", "templateKey":"LAPTOP"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.criteria.length()").value(7))
                .andExpect(jsonPath("$.constraints.length()").value(5))
                .andReturn().getResponse().getContentAsString();

        var id = mapper.readTree(response).get("id").asText();
        mvc.perform(get("/api/v1/decisions/{id}", id).with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 노트북"));
        mvc.perform(get("/api/v1/ai/capabilities").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("gemini"))
                .andExpect(jsonPath("$.requiresSensitiveDataAcknowledgement").value(true));
        mvc.perform(post("/api/v1/decisions/{id}/structure-proposals", id).with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"aiConsent":true,"sensitiveDataAcknowledged":false}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0]").value(org.hamcrest.Matchers.containsString("Gemini API")));
        var openApi = mvc.perform(get("/api/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andReturn().getResponse().getContentAsString();
        var output = Path.of("build", "generated", "openapi.json");
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), mapper.readTree(openApi));
    }

    @Test
    void savesCompleteDraftAndCreatesReproducibleAlgorithmEvaluation() throws Exception {
        var created = mapper.readTree(mvc.perform(post("/api/v1/decisions").with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"개발용 노트북", "templateKey":"LAPTOP"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        var decisionId = created.get("id").asText();

        var firstOptionId = UUID.randomUUID().toString();
        var secondOptionId = UUID.randomUUID().toString();
        var options = mapper.createArrayNode();
        options.add(option(firstOptionId, "Option A"));
        options.add(option(secondOptionId, "Option B"));

        var criteria = (tools.jackson.databind.node.ArrayNode) created.get("criteria");
        for (var criterion : criteria) {
            if ("NUMERIC_UTILITY".equals(criterion.get("kind").asText())) {
                var node = (tools.jackson.databind.node.ObjectNode) criterion;
                if ("LOWER_BETTER".equals(criterion.get("direction").asText())) {
                    node.put("worstAnchor", 100).put("bestAnchor", 0);
                } else {
                    node.put("worstAnchor", 0).put("bestAnchor", 100);
                }
            }
        }
        var values = mapper.createArrayNode();
        for (var optionId : new String[]{firstOptionId, secondOptionId}) {
            for (var criterion : criteria) {
                var value = mapper.createObjectNode()
                        .put("optionId", optionId)
                        .put("criterionId", criterion.get("id").asText())
                        .put("certainty", optionId.equals(firstOptionId) ? "CONFIRMED" : "ESTIMATED");
                if ("NUMERIC_UTILITY".equals(criterion.get("kind").asText())) {
                    value.put("numericValue", optionId.equals(firstOptionId) ? 60 : 80);
                } else {
                    value.put("rating", optionId.equals(firstOptionId) ? 3 : 5);
                }
                values.add(value);
            }
        }
        var constraints = (tools.jackson.databind.node.ArrayNode) created.get("constraints");
        constraints.forEach(node -> ((tools.jackson.databind.node.ObjectNode) node).put("active", false));

        var draft = mapper.createObjectNode()
                .put("expectedVersion", 0)
                .put("title", "개발용 노트북");
        draft.set("context", created.get("context"));
        draft.set("options", options);
        draft.set("criteria", criteria);
        draft.set("values", values);
        draft.set("constraints", constraints);
        draft.set("constraintResults", mapper.createArrayNode());

        var updated = mapper.readTree(mvc.perform(put("/api/v1/decisions/{id}/draft", decisionId).with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(draft)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString());

        var evaluation = mapper.readTree(mvc.perform(post("/api/v1/decisions/{id}/evaluations", decisionId)
                        .with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%d,"includeLlm":false,"aiConsent":false}
                                """.formatted(updated.get("version").asLong())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisionVersion").value(1))
                .andExpect(jsonPath("$.algorithmResult.recommendedOptionId").value(secondOptionId))
                .andExpect(jsonPath("$.algorithmResult.options[1].usesEstimatedValues").value(true))
                .andExpect(jsonPath("$.llmStatus").value("NOT_REQUESTED"))
                .andReturn().getResponse().getContentAsString());

        var other = createOtherUser();
        mvc.perform(get("/api/v1/decisions/{decisionId}/evaluations/{evaluationId}",
                        decisionId, evaluation.get("id").asText()).with(signedIn(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/decisions"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/decisions").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"CSRF 없는 요청", "templateKey":"CUSTOM"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/me").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID.toString()))
                .andExpect(jsonPath("$.email").value(TEST_USER.email()));
    }

    @Test
    void isolatesEveryDecisionOperationByOwner() throws Exception {
        var created = mapper.readTree(mvc.perform(post("/api/v1/decisions").with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Owner only", "templateKey":"CUSTOM"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        var decisionId = created.get("id").asText();
        var other = createOtherUser();

        mvc.perform(get("/api/v1/decisions").with(signedIn(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/decisions/{id}", decisionId).with(signedIn(other)))
                .andExpect(status().isNotFound());

        var draft = mapper.createObjectNode().put("expectedVersion", 0).put("title", "Forbidden update");
        draft.set("context", created.get("context"));
        draft.set("options", created.get("options"));
        draft.set("criteria", created.get("criteria"));
        draft.set("values", created.get("values"));
        draft.set("constraints", created.get("constraints"));
        draft.set("constraintResults", created.get("constraintResults"));
        mvc.perform(put("/api/v1/decisions/{id}/draft", decisionId).with(signedIn(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(draft)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/decisions/{id}/evaluations", decisionId).with(signedIn(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"includeLlm":false,"aiConsent":false}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/decisions/{id}/structure-proposals", decisionId)
                        .with(signedIn(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"aiConsent":true,"sensitiveDataAcknowledged":true}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/decisions/{id}/resolutions", decisionId).with(signedIn(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"DEFERRED"}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/decisions/{id}/outcome-check-ins", decisionId)
                        .with(signedIn(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"satisfaction":3,"expectationStatus":"MET"}
                                """))
                .andExpect(status().isNotFound());
    }

    private tools.jackson.databind.node.ObjectNode option(String id, String name) {
        return mapper.createObjectNode().put("id", id).put("name", name);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor signedIn() {
        return signedIn(TEST_USER);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor signedIn(AppUserPrincipal principal) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private AppUserPrincipal createOtherUser() {
        var id = UUID.randomUUID();
        var principal = new AppUserPrincipal(id, "other-" + id + "@example.com", "Other");
        users.save(new ApplicationUser(
                id, principal.email(), principal.email().toLowerCase(), principal.displayName(), Instant.now()));
        return principal;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfiguration {
        @Bean
        SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            SecurityConfiguration.configureCsrf(http);
            http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/openapi", "/actuator/health", "/actuator/info").permitAll()
                            .anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                            request -> request.getRequestURI().startsWith("/api/")));
            return http.build();
        }
    }
}
