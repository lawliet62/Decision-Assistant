package com.decisionassistant.guidance;

import com.decisionassistant.common.InvalidDecisionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicUrlPolicyTest {
    private final PublicUrlPolicy policy = new PublicUrlPolicy();

    @Test
    void acceptsPublicHttpUrlsAndRejectsLocalOrCredentialedTargets() {
        assertThat(policy.validate("https://example.com/product?q=1")).isEqualTo("https://example.com/product?q=1");
        assertThatThrownBy(() -> policy.validate("http://localhost:8080/admin"))
                .isInstanceOf(InvalidDecisionException.class);
        assertThatThrownBy(() -> policy.validate("http://192.168.1.2/admin"))
                .isInstanceOf(InvalidDecisionException.class);
        assertThatThrownBy(() -> policy.validate("https://user:secret@example.com/private"))
                .isInstanceOf(InvalidDecisionException.class);
        assertThatThrownBy(() -> policy.validate("file:///etc/passwd"))
                .isInstanceOf(InvalidDecisionException.class);
    }
}
