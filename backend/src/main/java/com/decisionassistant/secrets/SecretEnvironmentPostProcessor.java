package com.decisionassistant.secrets;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.function.BiFunction;

/** Runs after config data, before datasource/OIDC/AI clients are constructed. */
public final class SecretEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String SOURCE = "identitySecrets";
    private static final String PREFIX = "decision-assistant.secrets.";
    private final Log log;
    private final BiFunction<Map<String, String>, Boolean, Map<String, String>> loader;

    public SecretEnvironmentPostProcessor(DeferredLogFactory logs) {
        this(logs, GcpSecretLoader::load);
    }

    SecretEnvironmentPostProcessor(DeferredLogFactory logs,
            BiFunction<Map<String, String>, Boolean, Map<String, String>> loader) {
        this.log = logs.getLog(getClass());
        this.loader = loader;
    }

    @Override public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 1; }

    @Override public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        long started = System.nanoTime();
        try {
            configure(environment);
            log.info("secret_bootstrap outcome=success duration_ms=" + (System.nanoTime() - started) / 1_000_000);
        } catch (SecretBootstrapException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            // Do not pass binding errors, property values, SDK messages or their causes to startup logging.
            throw new SecretBootstrapException("CONFIGURATION_INVALID");
        }
    }

    private void configure(ConfigurableEnvironment env) {
        boolean local = env.matchesProfiles("local");
        boolean test = env.matchesProfiles("test");
        boolean oidc = env.matchesProfiles("oidc");
        if ((local && test) || (oidc && (local || test))) fail("PROFILE_CONFLICT");
        var values = new LinkedHashMap<String, Object>();
        if (test) {
            // Even inherited managed mode/ADC/API credentials cannot activate cloud calls in tests.
            for (Slot slot : Slot.values()) if (slot != Slot.DB && slot != Slot.AUTH0) values.put(slot.property, "test");
            values.put(PREFIX + "mode", "offline");
            values.put("decision-assistant.ai.enabled", false);
            values.put("decision-assistant.reminders.enabled", false);
            // Only explicit Spring test fixtures may enable mocked lookup/import, not the shell environment.
            var fixtures = env.getPropertySources().get("Inlined Test Properties");
            for (String feature : new String[]{"web", "search"}) {
                String key = "decision-assistant." + feature + ".enabled";
                values.put(key, fixtures != null && "true".equals(String.valueOf(fixtures.getProperty(key))));
            }
            values.put("spring.ai.model.chat", "none");
            install(env, values);
            return;
        }
        String mode = property(env, PREFIX + "mode", local ? "offline" : "gcp");
        if (!mode.equals("gcp") && !mode.equals("offline")) fail("MODE_INVALID");
        if (!local && (!oidc || !mode.equals("gcp"))) fail("PRODUCTION_REQUIRES_OIDC_AND_GCP");
        for (Slot slot : Slot.values()) {
            String existing = property(env, slot.property, "");
            boolean localDb = local && slot == Slot.DB && existing.equals("decision_assistant");
            if ((!existing.isBlank() && !existing.equals("disabled") && !localDb)
                    || !env.getProperty(slot.legacy, "").isBlank()
                    || !env.getProperty(slot.property.replace("-", "").replace('.', '_').toUpperCase(Locale.ROOT), "").isBlank()
                    || !env.getProperty(slot.property.replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT), "").isBlank()) {
                fail("PLAINTEXT_INPUT_" + slot.name());
            }
            // Even a dummy Auth0 property creates an OAuth registration and triggers client-id validation.
            if (slot != Slot.DB && slot != Slot.AUTH0) values.put(slot.property, "disabled");
        }
        var required = new LinkedHashMap<Slot, String>();
        if (!local) {
            required.put(Slot.DB, "");
            required.put(Slot.AUTH0, "");
        } else if (!property(env, PREFIX + "versions.db-password", "").isBlank()) {
            required.put(Slot.DB, ""); // Optional shared development DB, never the prod project.
        }
        boolean ai = enabled(env, "ai");
        String chat = "none";
        if (ai) {
            String provider = property(env, "decision-assistant.ai.provider", "gemini");
            if (provider.equals("gemini")) { required.put(Slot.GEMINI, ""); chat = "google-genai"; }
            else if (provider.equals("openai")) { required.put(Slot.OPENAI, ""); chat = "openai"; }
            else fail("AI_PROVIDER_INVALID");
        }
        if (enabled(env, "web")) required.put(Slot.FIRECRAWL, "");
        if (enabled(env, "search")) required.put(Slot.SERPER, "");
        if (enabled(env, "reminders")) required.put(Slot.RESEND, "");
        values.put("spring.ai.model.chat", chat);
        if (mode.equals("offline")) {
            if (!required.isEmpty()) fail("OFFLINE_EXTERNAL_FEATURE_ENABLED");
            install(env, values);
            return;
        }

        String dev = property(env, PREFIX + "dev-project-id", "");
        String prod = property(env, PREFIX + "prod-project-id", "");
        if (!projectId(dev) || !projectId(prod) || dev.equals(prod)) fail("DISTINCT_PROJECT_IDS_REQUIRED");
        String project = local ? dev : prod;
        var resources = new LinkedHashMap<String, String>();
        for (Slot slot : required.keySet()) {
            String version = property(env, PREFIX + "versions." + slot.id, "");
            if (!version.matches("[1-9][0-9]*")) fail("VERSION_REQUIRED_" + slot.name());
            resources.put(slot.property, "projects/" + project + "/secrets/" + slot.id + "/versions/" + version);
        }
        if (!resources.isEmpty()) {
            // Validate everything before the first network call; publish nothing on partial failure.
            Map<String, String> loaded = loader.apply(Map.copyOf(resources), local);
            for (String key : resources.keySet()) {
                String value = loaded.get(key);
                if (value == null || value.isBlank() || value.equals("disabled")) fail("EMPTY_PAYLOAD");
                values.put(key, value);
            }
        }
        install(env, values);
    }

    private static boolean projectId(String id) { return id.matches("[a-z][a-z0-9-]{4,28}[a-z0-9]"); }
    private static boolean enabled(ConfigurableEnvironment env, String feature) {
        return Binder.get(env).bind("decision-assistant." + feature + ".enabled", Boolean.class).orElse(false);
    }
    private static String property(ConfigurableEnvironment env, String key, String fallback) {
        return Binder.get(env).bind(key, String.class).orElse(fallback);
    }
    private static void install(ConfigurableEnvironment env, Map<String, Object> values) {
        env.getPropertySources().addFirst(new MapPropertySource(SOURCE, values) {
            @Override public String toString() { return "IdentitySecretPropertySource [values redacted]"; }
        });
    }
    private static void fail(String code) { throw new SecretBootstrapException(code); }

    private enum Slot {
        GEMINI("gemini-api-key", "spring.ai.google.genai.api-key", "GEMINI_API_KEY"),
        OPENAI("openai-api-key", "spring.ai.openai.api-key", "OPENAI_API_KEY"),
        SERPER("serper-api-key", "decision-assistant.search.api-key", "SERPER_API_KEY"),
        FIRECRAWL("firecrawl-api-key", "decision-assistant.web.api-key", "FIRECRAWL_API_KEY"),
        RESEND("resend-api-key", "decision-assistant.reminders.resend-api-key", "RESEND_API_KEY"),
        AUTH0("auth0-client-secret", "spring.security.oauth2.client.registration.auth0.client-secret", "AUTH0_CLIENT_SECRET"),
        DB("db-password", "spring.datasource.password", "DB_PASSWORD");
        final String id;
        final String property;
        final String legacy;
        Slot(String id, String property, String legacy) { this.id = id; this.property = property; this.legacy = legacy; }
    }
}
