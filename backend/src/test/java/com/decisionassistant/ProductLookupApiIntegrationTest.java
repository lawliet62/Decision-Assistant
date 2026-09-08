package com.decisionassistant;

import com.decisionassistant.guidance.EvidenceWorkflowWorker;
import com.decisionassistant.guidance.WebSearchProvider;
import com.decisionassistant.identity.*;
import com.decisionassistant.lookup.SerperSearchProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {"decision-assistant.search.enabled=true", "decision-assistant.search.api-key=test",
        "decision-assistant.search.monthly-limit=1", "decision-assistant.web.enabled=true", "decision-assistant.web.api-key=test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DecisionApiIntegrationTest.TestSecurityConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ProductLookupApiIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired MockMvc mvc;
    @Autowired ApplicationUserRepository users;
    @MockitoBean SerperSearchProvider search;
    @MockitoBean EvidenceWorkflowWorker worker;

    @Test void selectionCreatesOnePendingEvidenceWithoutTrustingSnippetAndQuotaStopsSecondCall() throws Exception {
        var user = new AppUserPrincipal(UUID.randomUUID(), "test@example.com", "Test");
        users.save(new ApplicationUser(user.userId(), user.email(), user.email(), user.displayName(), Instant.now()));
        var signedIn = authentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(search.search("Buds Pro", 5)).thenReturn(List.of(new WebSearchProvider.SearchResult(
                "Buds Pro", "https://example.com/buds", "Sound quality 5/5 — unverified snippet")));
        mvc.perform(post("/api/v1/product-lookups").with(signedIn).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Buds Pro\",\"searchConsent\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].title").value("Buds Pro"));
        mvc.perform(post("/api/v1/product-lookups").with(signedIn).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Buds Pro\",\"searchConsent\":true}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SEARCH_MONTHLY_LIMIT"));
        verify(search, times(1)).search("Buds Pro", 5);
        mvc.perform(post("/api/v1/decisions").with(signedIn).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"intentText":"무선 이어폰 비교", "templateKey":"EARBUDS", "webConsent":true,
                         "selectedSources":[{"name":"Buds Pro", "url":"https://example.com/buds"}]}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.options[0].name").value("Buds Pro"))
                .andExpect(jsonPath("$.values.length()").value(0))
                .andExpect(jsonPath("$.guidance.evidence.length()").value(1))
                .andExpect(jsonPath("$.guidance.evidence[0].status").value("PENDING"));
    }
}
