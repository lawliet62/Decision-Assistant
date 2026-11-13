package com.decisionassistant.secrets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class SecretEnvironmentPostProcessorTest {
    private static final String SEARCH_KEY = "decision-assistant.search.api-key";

    private MockEnvironment managed(String profile) {
        var env = new MockEnvironment();
        env.setActiveProfiles(profile);
        return env.withProperty("decision-assistant.secrets.mode", "gcp")
                .withProperty("decision-assistant.secrets.dev-project-id", "decision-dev")
                .withProperty("decision-assistant.secrets.prod-project-id", "decision-prod");
    }

    private SecretEnvironmentPostProcessor noNetwork() {
        return new SecretEnvironmentPostProcessor(new DeferredLogs(), (resources, local) -> {
            throw new AssertionError("Cloud must not be contacted");
        });
    }

    @Test void localOfflineNeedsNeitherCloudNorCredentials() {
        var env = new MockEnvironment();
        env.setActiveProfiles("local");
        noNetwork().postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(env.getProperty(SEARCH_KEY)).isEqualTo("disabled");
        assertThat(env.getProperty("spring.security.oauth2.client.registration.auth0.client-secret")).isNull();
    }

    @Test void catalogResearchLoadsOnlyOpenAiWithoutEnablingChat() {
        var env=managed("local").withProperty("decision-assistant.catalog.research-enabled","true")
                .withProperty("decision-assistant.secrets.versions.openai-api-key","1");
        new SecretEnvironmentPostProcessor(new DeferredLogs(),(resources,local)->{
            assertThat(resources).containsOnlyKeys("spring.ai.openai.api-key");
            return Map.of("spring.ai.openai.api-key","fixture-only-key");
        }).postProcessEnvironment(env,null);
        assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(env.getProperty("spring.ai.openai.api-key")).isEqualTo("fixture-only-key");
    }

    @Test void offlineDoesNotSilentlyDisableRequestedFeature() {
        var env = new MockEnvironment().withProperty("decision-assistant.search.enabled", "true");
        env.setActiveProfiles("local");
        assertThatThrownBy(() -> noNetwork().postProcessEnvironment(env, null))
                .hasMessageContaining("OFFLINE_EXTERNAL_FEATURE_ENABLED");
    }

    @Test void loadsOnlyEnabledSecretAndBindsExistingProperties() {
        var env = managed("local").withProperty("decision-assistant.search.enabled", "true")
                .withProperty("decision-assistant.search.monthly-limit", "1000")
                .withProperty("decision-assistant.secrets.versions.serper-api-key", "3");
        var processor = new SecretEnvironmentPostProcessor(new DeferredLogs(), (resources, local) -> {
            assertThat(local).isTrue();
            assertThat(resources).containsExactlyEntriesOf(Map.of(SEARCH_KEY,
                    "projects/decision-dev/secrets/serper-api-key/versions/3"));
            return Map.of(SEARCH_KEY, "fixture-only-key");
        });
        processor.postProcessEnvironment(env, null);
        var properties = Binder.get(env).bind("decision-assistant.search", com.decisionassistant.lookup.SearchProperties.class).get();
        assertThat(properties.configured()).isTrue();
        assertThat(properties.apiKey()).isEqualTo("fixture-only-key");
        assertThat(env.getPropertySources().get(SecretEnvironmentPostProcessor.SOURCE).toString())
                .doesNotContain("fixture-only-key");
    }

    @Test void productionRequiresDbAndOidcAndSelectsOnlyProd() {
        var env = managed("oidc").withProperty("decision-assistant.secrets.versions.db-password", "2")
                .withProperty("decision-assistant.secrets.versions.auth0-client-secret", "4");
        var processor = new SecretEnvironmentPostProcessor(new DeferredLogs(), (resources, local) -> {
            assertThat(local).isFalse();
            assertThat(resources).hasSize(2);
            assertThat(resources.values()).allMatch(value -> value.startsWith("projects/decision-prod/"));
            var result = new HashMap<String, String>();
            resources.forEach((key, resource) -> result.put(key, "fake-" + key));
            return result;
        });
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.datasource.password")).startsWith("fake-");
        assertThat(env.getProperty("spring.security.oauth2.client.registration.auth0.client-secret")).startsWith("fake-");
    }

    @Test void validatesAllReferencesBeforeCallingCloudAndRequiresPinnedVersions() {
        for (String version : new String[]{"", "latest", "0", "projects/decision-prod/secrets/x/versions/1"}) {
            var env = managed("local").withProperty("decision-assistant.search.enabled", "true")
                    .withProperty("decision-assistant.secrets.versions.serper-api-key", version);
            assertThatThrownBy(() -> noNetwork().postProcessEnvironment(env, null))
                    .hasMessageContaining("VERSION_REQUIRED_SERPER");
        }
        var same = managed("local").withProperty("decision-assistant.secrets.prod-project-id", "decision-dev");
        assertThatThrownBy(() -> noNetwork().postProcessEnvironment(same, null))
                .hasMessageContaining("DISTINCT_PROJECT_IDS_REQUIRED");
    }

    @Test void refusesProductionOfflineAndMixedProfiles() {
        var offline = managed("oidc").withProperty("decision-assistant.secrets.mode", "offline");
        assertThatThrownBy(() -> noNetwork().postProcessEnvironment(offline, null))
                .hasMessageContaining("PRODUCTION_REQUIRES_OIDC_AND_GCP");
        var mixed = managed("local");
        mixed.setActiveProfiles("local", "oidc");
        assertThatThrownBy(() -> noNetwork().postProcessEnvironment(mixed, null)).hasMessageContaining("PROFILE_CONFLICT");
    }

    @Test void refusesLegacyAndCanonicalPlaintextWithoutEchoingThem() {
        for (String key : new String[]{"SERPER_API_KEY", SEARCH_KEY, "GEMINI_API_KEY", "DB_PASSWORD"}) {
            var env = managed("local").withProperty(key, "sensitive-fixture");
            assertThatThrownBy(() -> noNetwork().postProcessEnvironment(env, null))
                    .isInstanceOf(SecretBootstrapException.class).hasMessageContaining("PLAINTEXT_INPUT")
                    .hasMessageNotContaining("sensitive-fixture").hasNoCause();
        }
        var env = managed("local");
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("DECISIONASSISTANT_SEARCH_APIKEY", "sensitive-fixture")));
        assertThat(Binder.get(env).bind(SEARCH_KEY, String.class).orElse(null)).isEqualTo("sensitive-fixture");
        assertThatThrownBy(() -> noNetwork().postProcessEnvironment(env, null)).hasMessageContaining("PLAINTEXT_INPUT");
    }

    @Test void cloudFailureAndPartialResultsNeverInstallSecretsOrFallback() {
        for (boolean partial : new boolean[]{true, false}) {
            var env = managed("local").withProperty("decision-assistant.search.enabled", "true")
                    .withProperty("decision-assistant.secrets.versions.serper-api-key", "1");
            var processor = new SecretEnvironmentPostProcessor(new DeferredLogs(), (resources, local) -> {
                if (partial) return Map.of();
                throw new IllegalStateException("raw-sdk-sensitive-fixture");
            });
            assertThatThrownBy(() -> processor.postProcessEnvironment(env, null))
                    .isInstanceOf(SecretBootstrapException.class).hasMessageNotContaining("sensitive-fixture").hasNoCause();
            assertThat(env.getPropertySources().contains(SecretEnvironmentPostProcessor.SOURCE)).isFalse();
        }
    }

    @Test void testProfileIgnoresInheritedCloudFlagsAndCredentials() {
        var env = managed("test").withProperty("decision-assistant.search.enabled", "true")
                .withProperty("decision-assistant.web.enabled", "true")
                .withProperty("decision-assistant.ai.enabled", "true")
                .withProperty("SERPER_API_KEY", "must-not-be-used");
        noNetwork().postProcessEnvironment(env, null);
        assertThat(env.getProperty("decision-assistant.secrets.mode")).isEqualTo("offline");
        assertThat(env.getProperty("decision-assistant.ai.enabled")).isEqualTo("false");
        assertThat(env.getProperty("decision-assistant.search.enabled")).isEqualTo("false");
        assertThat(env.getProperty("decision-assistant.web.enabled")).isEqualTo("false");
        assertThat(env.getProperty(SEARCH_KEY)).isEqualTo("test");
        assertThat(env.getProperty("spring.security.oauth2.client.registration.auth0.client-secret")).isNull();
    }

    @Test void selectsOnlyConfiguredAiProviderAndDisablesUnselectedClient() {
        for (String provider : new String[]{"gemini", "openai"}) {
            var env = managed("local").withProperty("decision-assistant.ai.enabled", "true")
                    .withProperty("decision-assistant.ai.provider", provider)
                    .withProperty("decision-assistant.secrets.versions." + provider + "-api-key", "1");
            var processor = new SecretEnvironmentPostProcessor(new DeferredLogs(), (resources, local) -> {
                assertThat(resources).hasSize(1);
                assertThat(resources.values().iterator().next()).contains("/" + provider + "-api-key/");
                return Map.of(resources.keySet().iterator().next(), "fake-key");
            });
            processor.postProcessEnvironment(env, null);
            assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo(provider.equals("gemini") ? "google-genai" : "openai");
        }
    }

    @Test void registeredBootstrapRunsAfterYamlAndBeforeBeanConstruction() {
        // Real SpringApplication lifecycle, but no auto-configured DB/web/cloud clients.
        var env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        var app = new SpringApplication(ProbeConfiguration.class);
        app.setEnvironment(env);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run("--spring.profiles.active=local", "--spring.main.banner-mode=off")) {
            assertThat(context.getBean(AtomicBoolean.class)).isTrue();
            assertThat(context.getEnvironment().getProperty("spring.datasource.password")).isEqualTo("decision_assistant");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean AtomicBoolean bootstrapVisible(ConfigurableEnvironment env) {
            return new AtomicBoolean(env.getPropertySources().contains(SecretEnvironmentPostProcessor.SOURCE)
                    && "none".equals(env.getProperty("spring.ai.model.chat")));
        }
    }
}
