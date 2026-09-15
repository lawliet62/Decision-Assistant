package com.decisionassistant.secrets;

import com.decisionassistant.guidance.WebProperties;
import com.decisionassistant.lookup.SearchProperties;
import com.decisionassistant.notification.ReminderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;

class SecretConsumerBindingTest {
    @Test void enabledConsumersUseBootstrapValuesAndGoogleClientStillConstructsWithoutAdc() throws Exception {
        var env = new MockEnvironment()
                .withProperty("decision-assistant.secrets.mode", "gcp")
                .withProperty("decision-assistant.secrets.dev-project-id", "decision-dev")
                .withProperty("decision-assistant.secrets.prod-project-id", "decision-prod")
                .withProperty("decision-assistant.ai.enabled", "true")
                .withProperty("decision-assistant.web.enabled", "true")
                .withProperty("decision-assistant.search.enabled", "true")
                .withProperty("decision-assistant.reminders.enabled", "true")
                .withProperty("decision-assistant.search.monthly-limit", "1000");
        env.setActiveProfiles("local");
        for (String secret : new String[]{"gemini-api-key", "firecrawl-api-key", "serper-api-key", "resend-api-key"}) {
            env.setProperty("decision-assistant.secrets.versions." + secret, "1");
        }
        new SecretEnvironmentPostProcessor(new DeferredLogs(), (resources, local) -> {
            assertThat(resources).hasSize(4);
            var result = new HashMap<String, String>();
            resources.forEach((key, resource) -> result.put(key, "fake-consumer-key"));
            return result;
        }).postProcessEnvironment(env, null);
        var binder = Binder.get(env);
        assertThat(binder.bind("decision-assistant.web", WebProperties.class).get().configured()).isTrue();
        assertThat(binder.bind("decision-assistant.search", SearchProperties.class).get().configured()).isTrue();
        assertThat(binder.bind("decision-assistant.reminders", ReminderProperties.class).get().configured()).isTrue();
        var google = binder.bind("spring.ai.google.genai", GoogleGenAiConnectionProperties.class).get();
        assertThat(google.getApiKey()).isEqualTo("fake-consumer-key");
        // Uses the real Spring AI/Google SDK construction path, but never issues an API request.
        try (var client = new GoogleGenAiChatAutoConfiguration().googleGenAiClient(google)) {
            assertThat(client).isNotNull();
        }
    }

    @Test void propertyRecordDiagnosticsDoNotExposeKeys() {
        assertThat(new SearchProperties(true, "sensitive-fixture", 1000).toString()).doesNotContain("sensitive-fixture");
        assertThat(new WebProperties(true, "https://example.com", "sensitive-fixture", Duration.ofSeconds(1)).toString())
                .doesNotContain("sensitive-fixture");
        assertThat(new ReminderProperties(true, "https://example.com", "sensitive-fixture", "from@example.com").toString())
                .doesNotContain("sensitive-fixture");
    }
}
