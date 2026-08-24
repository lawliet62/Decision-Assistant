package com.decisionassistant.ai;

import com.decisionassistant.common.InvalidDecisionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiDataPolicyTest {

    @Test
    void requiresSensitiveDataAcknowledgementForGemini() {
        var policy = new AiDataPolicy(new AiProperties(false, "gemini", "gemini-2.5-flash-lite", Duration.ofSeconds(30)));

        assertThat(policy.requiresSensitiveDataAcknowledgement()).isTrue();
        assertThatThrownBy(() -> policy.validateSensitiveDataAcknowledgement(false))
                .isInstanceOf(InvalidDecisionException.class)
                .hasMessageContaining("not ready");
        policy.validateSensitiveDataAcknowledgement(true);
    }

    @Test
    void doesNotRequireSensitiveDataAcknowledgementForOpenAi() {
        var policy = new AiDataPolicy(new AiProperties(false, "openai", "gpt-5.4-nano", Duration.ofSeconds(30)));

        assertThat(policy.requiresSensitiveDataAcknowledgement()).isFalse();
        policy.validateSensitiveDataAcknowledgement(false);
    }
}
