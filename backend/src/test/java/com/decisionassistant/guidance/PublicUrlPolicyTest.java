package com.decisionassistant.guidance;

import com.decisionassistant.common.InvalidDecisionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicUrlPolicyTest {
    private final PublicUrlPolicy policy = new PublicUrlPolicy();

    @Test
    void acceptsPublicHttpUrlsAndRejectsLocalOrCredentialedTargets() {
        assertThat(policy.validate("https://fdesign.example/product")).isEqualTo("https://fdesign.example/product");
        for (var url : java.util.List.of("http://[::1]/", "http://[fd00::1]/", "http://[fe80::1]/", "http://localhost./", "http://2130706433/")) {
            assertThatThrownBy(() -> policy.validate(url)).isInstanceOf(InvalidDecisionException.class);
        }
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
