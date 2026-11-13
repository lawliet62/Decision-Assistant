package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SennheiserSpecificationVerifierTest {
    private static final String PAGE = """
            <link rel='canonical' href='https://kr.sennheiser-hearing.com/product/모멘텀-트루-와이어리스-5/731/'>
            <h1>모멘텀 트루 와이어리스 5</h1>
            <script type='application/ld+json'>{"@type":"ProductGroup","name":"모멘텀 트루 와이어리스 5","productGroupID":"cafe24_bnispace_1_731"}</script>
            <div class='table-wrapper'>
              <div class='table-row'><div class='label'>액티브 노이즈 캔슬링</div><div class='description'>하이브리드 적응형 ANC</div></div>
              <div class='table-row'><div class='label'>방진·방수 등급</div><div class='description'>IP54 방진 및 생활 방수 (이어버드만 해당)</div></div>
            </div>
            """;
    @Test void requiresSameProductAndSingleMatchingSpecification() {
        assertThat(SennheiserSpecificationVerifier.matches(PAGE, "ANC 지원")).isTrue();
        assertThat(SennheiserSpecificationVerifier.matches(PAGE, "방수")).isTrue();
        assertThat(SennheiserSpecificationVerifier.matches(PAGE.replace("IP54", "IP55"), "방수")).isFalse();
        assertThat(SennheiserSpecificationVerifier.matches(PAGE.replace("하이브리드 적응형 ANC", "지원하지 않음"), "ANC 지원")).isFalse();
        assertThat(SennheiserSpecificationVerifier.matches(PAGE.replace("cafe24_bnispace_1_731", "cafe24_bnispace_1_732"), "방수")).isFalse();
        assertThat(SennheiserSpecificationVerifier.matches(PAGE.replace("/731/", "/732/"), "방수")).isFalse();
        assertThat(SennheiserSpecificationVerifier.matches(PAGE.replace("<div class='table-wrapper'>", "<div class='table-wrapper'><div class='table-row'><div class='label'>방진·방수 등급</div><div class='description'>IP55</div></div>"), "방수")).isFalse();
        assertThat(SennheiserSpecificationVerifier.matches(PAGE, "호환성")).isFalse();
    }
    @Test void requiresTheExactEventHeadingGroupAndSpecification() {
        var event = PAGE.replace("/product/모멘텀-", "/product/젠사페-모멘텀-")
                .replace("모멘텀 트루 와이어리스 5", "[젠사페] 모멘텀 트루 와이어리스 5");
        assertThat(SennheiserSpecificationVerifier.matches(event, "ANC 지원")).isTrue();
        assertThat(SennheiserSpecificationVerifier.matches(event, "방수")).isTrue();
        for (var bad : java.util.List.of(event.replaceFirst("\\[젠사페\\] ", ""),
                event.replace("/product/젠사페-", "/product/"), event.replace("[젠사페]", "[임의행사]"),
                event.replace("cafe24_bnispace_1_731", "cafe24_bnispace_1_732"), event.replace("IP54", "IP55")))
            assertThat(SennheiserSpecificationVerifier.matches(bad, "방수")).isFalse();
    }
}
