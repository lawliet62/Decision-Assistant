package com.decisionassistant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DecisionApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();

    @Test
    void bootsMigratesDatabaseAndCreatesLaptopDecision() throws Exception {
        var response = mvc.perform(post("/api/v1/decisions")
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
        mvc.perform(get("/api/v1/decisions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 노트북"));
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
        var created = mapper.readTree(mvc.perform(post("/api/v1/decisions")
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

        var updated = mapper.readTree(mvc.perform(put("/api/v1/decisions/{id}/draft", decisionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(draft)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/decisions/{id}/evaluations", decisionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%d,"includeLlm":false,"aiConsent":false}
                                """.formatted(updated.get("version").asLong())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisionVersion").value(1))
                .andExpect(jsonPath("$.algorithmResult.recommendedOptionId").value(secondOptionId))
                .andExpect(jsonPath("$.algorithmResult.options[1].usesEstimatedValues").value(true))
                .andExpect(jsonPath("$.llmStatus").value("NOT_REQUESTED"));
    }

    private tools.jackson.databind.node.ObjectNode option(String id, String name) {
        return mapper.createObjectNode().put("id", id).put("name", name);
    }
}
