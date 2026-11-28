package com.decisionassistant.job;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobModeEnvironmentPostProcessorTest {
    private static final String OAUTH2_CLIENT =
            "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,"
            + "org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration";
    private final JobModeEnvironmentPostProcessor processor = new JobModeEnvironmentPostProcessor();

    @Test void absentOrBlankSwitchLeavesNormalModeUnchanged() {
        for (var env : new MockEnvironment[]{new MockEnvironment(), new MockEnvironment().withProperty(JobMode.PROPERTY, " ")}) {
            processor.postProcessEnvironment(env, null);
            assertThat(JobMode.active(env)).isFalse();
            assertThat(env.getPropertySources().contains(JobModeEnvironmentPostProcessor.SOURCE)).isFalse();
            assertThat(env.getProperty("spring.main.web-application-type")).isNull();
        }
    }

    @Test void switchAloneSelectsNonWebWithoutMigrationOrOidcDiscovery() {
        var env = new MockEnvironment().withProperty(JobMode.PROPERTY, JobMode.PURGE_EXPIRED_DECISIONS)
                .withProperty("spring.flyway.enabled", "true");
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.main.web-application-type")).isEqualTo("none");
        assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(env.getProperty("spring.autoconfigure.exclude")).isEqualTo(OAUTH2_CLIENT);
    }

    @Test void keepsExistingAutoConfigurationExclusions() {
        var env = new MockEnvironment().withProperty(JobMode.PROPERTY, JobMode.PURGE_EXPIRED_DECISIONS)
                .withProperty("spring.autoconfigure.exclude", "com.example.Other");
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.autoconfigure.exclude")).isEqualTo("com.example.Other," + OAUTH2_CLIENT);
    }

    @Test void unknownJobNameFailsWithSupportedNames() {
        var env = new MockEnvironment().withProperty(JobMode.PROPERTY, "purge-everything");
        assertThatThrownBy(() -> processor.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("purge-everything").hasMessageContaining(JobMode.PURGE_EXPIRED_DECISIONS);
    }
}
