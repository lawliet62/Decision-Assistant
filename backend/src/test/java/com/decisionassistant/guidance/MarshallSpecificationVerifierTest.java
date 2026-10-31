package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MarshallSpecificationVerifierTest {
    @Test void minorIvRequiresExactModelAndSingleWaterResistanceRow() {
        var url = "https://www.marshall.com/kr/ko/product/minor-iv?pid=1006653";
        var page = "<link rel='canonical' href='" + url + "'><h1>MINOR IV</h1>"
                + "<script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"MINOR IV\",\"sku\":\"1006653\",\"url\":\"" + url + "\"}</script>"
                + "<div><div>방수 </div><div>이어버드 IPX4</div></div>";
        assertThat(MarshallSpecificationVerifier.matchesMinorIvWater(page)).isTrue();
        assertThat(MarshallSpecificationVerifier.matchesMinorIvWater(page.replace("1006653", "1006654"))).isFalse();
        assertThat(MarshallSpecificationVerifier.matchesMinorIvWater(page.replace("IPX4", "IPX5"))).isFalse();
        assertThat(MarshallSpecificationVerifier.matchesMinorIvWater(page.replace("MINOR IV", "MINOR III"))).isFalse();
        assertThat(MarshallSpecificationVerifier.matchesMinorIvWater(page + "<div>방수</div>")).isFalse();
        assertThat(MarshallSpecificationVerifier.matchesMinorIvWater(page.replace("<div>이어버드 IPX4</div>", "<div>이어버드 IPX4</div><div>IPX5</div>"))).isFalse();
    }
    private static final String URL = "https://www.marshall.com/kr/ko/product/motif-ii-anc?pid=1006450";
    private static final String PAGE = "<link rel='canonical' href='" + URL + "'><h1>MOTIF II A.N.C.</h1>"
            + "<script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"MOTIF II A.N.C.\",\"sku\":\"1006450\",\"url\":\"" + URL + "\"}</script>"
            + "<div><p>액티브 노이즈 캔슬링</p><div><p>예</p></div></div>";
    @Test void requiresExactModelAndAnUnambiguousAffirmativeRow() {
        assertThat(MarshallSpecificationVerifier.matches(PAGE)).isTrue();
        assertThat(MarshallSpecificationVerifier.matches(PAGE.replace("\"url\":\"" + URL, "\"url\":\"https://marshall-production-1.mobify-storefront.com/kr/ko/product/motif-ii-anc?pid=1006450"))).isTrue();
        assertThat(MarshallSpecificationVerifier.matches(PAGE.replace("\"url\":\"" + URL, "\"url\":\"https://untrusted.example/kr/ko/product/motif-ii-anc?pid=1006450"))).isFalse();
        assertThat(MarshallSpecificationVerifier.matches(PAGE.replace("1006450", "1006451"))).isFalse();
        assertThat(MarshallSpecificationVerifier.matches(PAGE.replace("<p>예</p>", "<p>아니요</p>"))).isFalse();
        assertThat(MarshallSpecificationVerifier.matches(PAGE + "<p>액티브 노이즈 캔슬링</p>")).isFalse();
        assertThat(MarshallSpecificationVerifier.matches(PAGE.replace("<p>예</p>", "<p>예</p><p>아니요</p>"))).isFalse();
    }
}
