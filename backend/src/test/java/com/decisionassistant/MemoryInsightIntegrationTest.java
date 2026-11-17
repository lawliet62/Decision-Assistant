package com.decisionassistant;

import com.decisionassistant.ai.*;
import com.decisionassistant.decision.MemoryInsightContracts;
import com.decisionassistant.identity.*;
import org.junit.jupiter.api.*;
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
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration") @Testcontainers @SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@Import(DecisionApiIntegrationTest.TestSecurityConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class MemoryInsightIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationUserRepository users;
    @MockitoBean AiProperties properties;
    @MockitoBean MemoryInsightContracts.Interpreter interpreter;
    final ObjectMapper mapper = new ObjectMapper();
    RequestPostProcessor owner;
    UUID source, current, resolution;

    @BeforeEach void setup() throws Exception {
        var id = UUID.randomUUID(); var email = id + "@example.com";
        users.saveAndFlush(new ApplicationUser(id, email, email, "요약 검증", Instant.now()));
        owner = authentication(UsernamePasswordAuthenticationToken.authenticated(new AppUserPrincipal(id, email, "요약 검증"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(properties.enabled()).thenReturn(true); when(properties.provider()).thenReturn("fixture");
        when(properties.model()).thenReturn("fixture-model"); when(properties.timeout()).thenReturn(Duration.ofSeconds(5));
        when(interpreter.interpret(any())).thenReturn(result());
        var past = create(); source = UUID.fromString(past.path("id").asText());
        mvc.perform(post("/api/v1/decisions/{id}/resolutions", source).with(owner).with(csrf()).contentType("application/json")
                .content("{\"kind\":\"CHOSEN\",\"selectedOptionId\":\"" + past.path("options").get(0).path("id").asText()
                        + "\",\"reasonIds\":[\"compatibility\"],\"rationale\":\"사용하던 휴대폰과 연동\"}"))
                .andExpect(status().isCreated());
        var memories = mapper.readTree(mvc.perform(get("/api/v1/decisions/{id}", source).with(owner)).andReturn().getResponse().getContentAsString());
        resolution = UUID.fromString(memories.path("resolutions").get(0).path("id").asText());
        current = UUID.fromString(create().path("id").asText());
    }
    JsonNode create() throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/decisions").with(owner).with(csrf()).contentType("application/json")
                .content("{\"intentText\":\"이어폰 비교\",\"templateKey\":\"EARBUDS\",\"selectedSources\":[{\"name\":\"Alpha\",\"url\":\"https://example.com/alpha\"}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    String url() { return "/api/v1/decisions/" + current + "/memories/" + resolution + "/insight"; }
    String hash() throws Exception {
        return mapper.readTree(mvc.perform(get(url()).with(owner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("inputHash").asText();
    }
    org.springframework.test.web.servlet.ResultActions generate(String hash) throws Exception {
        return mvc.perform(post(url()).with(owner).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("aiConsent", true, "sensitiveDataAcknowledged", true, "expectedInputHash", hash))));
    }
    AiContracts.AiResult<MemoryInsightContracts.Insight> result() {
        return new AiContracts.AiResult<>(new MemoryInsightContracts.Insight("연동을 이유로 선택했습니다.",
                "같은 연동이 필요하면 참고할 수 있습니다.", List.of("현재 사용하는 휴대폰 확인"), "구매·사용 결과는 미기록입니다."), 100, 50);
    }
    @Test void consentCacheMetadataAndInvalidation() throws Exception {
        var hash = hash();
        when(interpreter.interpret(any())).thenAnswer(call -> {
            assertThat(jdbc.queryForObject("select count(*) from memory_insight_inputs where decision_id=?", Integer.class, current)).isEqualTo(1);
            return result();
        });
        mvc.perform(post(url()).with(owner).with(csrf()).contentType("application/json").content("{\"expectedInputHash\":\"" + hash + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(interpreter);
        generate(hash).andExpect(status().isOk()).andExpect(jsonPath("$.insight.summary").isNotEmpty());
        generate(hash).andExpect(status().isOk()); verify(interpreter, times(1)).interpret(any());
        assertThat(jdbc.queryForObject("select input_tokens from memory_insights where decision_id=?", Long.class, current)).isEqualTo(100);
        assertThat(jdbc.queryForObject("select snapshot -> 'source' ->> 'rationale' from memory_insight_inputs where decision_id=?", String.class, current)).isEqualTo("사용하던 휴대폰과 연동");
        mvc.perform(get(url()).with(owner)).andExpect(jsonPath("$.saved.provider").value("fixture"));
        jdbc.update("update resolutions set rationale='변경된 이유' where id=?", resolution);
        mvc.perform(get(url()).with(owner)).andExpect(jsonPath("$.saved").doesNotExist());
        generate(hash).andExpect(status().isConflict());
        jdbc.update("update decisions set personalization_enabled=false where id=?", source);
        mvc.perform(get(url()).with(owner)).andExpect(status().isNotFound());
    }
    @Test void deletionDuringProviderCallCannotStoreSummary() throws Exception {
        var hash = hash();
        when(interpreter.interpret(any())).thenAnswer(call -> { jdbc.update("delete from decisions where id=?", source); return result(); });
        generate(hash).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from memory_insights where decision_id=?", Integer.class, current)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from memory_insight_inputs where decision_id=?", Integer.class, current)).isZero();
    }
    @Test void sourceOptOutDuringProviderCallCannotStoreSummary() throws Exception {
        var hash = hash();
        when(interpreter.interpret(any())).thenAnswer(call -> { jdbc.update("update decisions set personalization_enabled=false where id=?", source); return result(); });
        generate(hash).andExpect(status().isNotFound());
    }
    @Test void hardDeleteCascadesDerivedSummary() throws Exception {
        generate(hash()).andExpect(status().isOk());
        jdbc.update("delete from decisions where id=?", source);
        assertThat(jdbc.queryForObject("select count(*) from memory_insights where decision_id=?", Integer.class, current)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from memory_insight_inputs where decision_id=?", Integer.class, current)).isZero();
    }
    @Test void foreignOwnerAndMalformedOutputAreRejected() throws Exception {
        var foreignId = UUID.randomUUID(); var email = foreignId + "@example.com";
        users.saveAndFlush(new ApplicationUser(foreignId, email, email, "다른 사용자", Instant.now()));
        var foreign = authentication(UsernamePasswordAuthenticationToken.authenticated(new AppUserPrincipal(foreignId, email, "다른 사용자"), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        mvc.perform(get(url()).with(foreign)).andExpect(status().isNotFound());
        mvc.perform(post(url()).with(foreign).with(csrf()).contentType("application/json").content("{\"aiConsent\":true,\"sensitiveDataAcknowledged\":true,\"expectedInputHash\":\"foreign\"}"))
                .andExpect(status().isNotFound());
        when(interpreter.interpret(any())).thenReturn(new AiContracts.AiResult<>(new MemoryInsightContracts.Insight(null, "", null, null), 1, 1));
        generate(hash()).andExpect(status().isUnprocessableEntity());
    }
    @Test void providerFailureLeavesOriginalUsable() throws Exception {
        when(interpreter.interpret(any())).thenThrow(new IllegalStateException("fixture failure"));
        generate(hash()).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/decisions/{id}/memories", current).with(owner)).andExpect(status().isOk()).andExpect(jsonPath("$[0].rationale").value("사용하던 휴대폰과 연동"));
    }
    @Test void currentInputChangesDuringCallRejectResult() throws Exception {
        var hash = hash();
        when(interpreter.interpret(any())).thenAnswer(call -> { jdbc.update("update decisions set title='다른 용도' where id=?", current); return result(); });
        generate(hash).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from memory_insights where decision_id=?", Integer.class, current)).isZero();
    }
    @Test void trashedOrCorrectingSourceCannotBeUsed() throws Exception {
        var hash = hash();
        jdbc.update("update decisions set edit_mode='CORRECTION' where id=?", source);
        generate(hash).andExpect(status().isNotFound());
        jdbc.update("update decisions set edit_mode=null, deleted_at=now() where id=?", source);
        mvc.perform(get(url()).with(owner)).andExpect(status().isNotFound());
        verifyNoInteractions(interpreter);
    }
}
