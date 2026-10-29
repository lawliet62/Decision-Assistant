package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AppleSpecificationVerifierTest {
    private static final String PAGE = """
            <title>AirPods Pro 3 - 제품 사양 - Apple (KR)</title>
            <div class='techspecs-section section-audio'><ul class='techspecs-list'><li>액티브 노이즈 캔슬링</li></ul></div>
            <div class='techspecs-section section-water'><ul class='techspecs-list'><li>먼지, 땀, 습기에 강한 방진 및 생활 방수 디자인(IP57): AirPods&nbsp;Pro&nbsp;및 충전 케이스</li></ul></div>
            """;
    @Test void acceptsOnlyExactModelAndUnambiguousSpecificationSection() {
        assertThat(AppleSpecificationVerifier.matches(PAGE, "ANC 지원")).isTrue();
        assertThat(AppleSpecificationVerifier.matches(PAGE, "방수")).isTrue();
        assertThat(AppleSpecificationVerifier.matches(PAGE.replace("AirPods Pro 3", "AirPods Pro 4"), "방수")).isFalse();
        assertThat(AppleSpecificationVerifier.matches(PAGE.replace("IP57", "IP54"), "방수")).isFalse();
        assertThat(AppleSpecificationVerifier.matches(PAGE.replace("<li>액티브 노이즈 캔슬링</li>", "<li>액티브 노이즈 캔슬링</li><li>액티브 노이즈 캔슬링 미지원</li>"), "ANC 지원")).isFalse();
        assertThat(AppleSpecificationVerifier.matches(PAGE.replace("section-water", "section-other") + "<aside>IP57</aside>", "방수")).isFalse();
    }
}
