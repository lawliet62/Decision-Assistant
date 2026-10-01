package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

class DiscoveryExclusionsTest {
    @Test void exclusionsMatchAllPreviousIdentitiesNamesAndCanonicalUrlsButKeepDistinctConfigurations() {
        var seen = new CandidateSuggestion(UUID.randomUUID(), "Buds Pro 2", "https://qcystore.co.kr/product/detail.html?product_no=1", "", "test", Instant.now());
        seen.officialProduct(product("Buds Pro 2", "sku-1", seen.url()));
        var exclusions = DiscoveryExclusions.from(List.of(seen), List.of("사용자 개인 입력"));
        assertThat(exclusions.contains(product("완전히 다른 표기", "sku-1", "https://qcystore.co.kr/product/model/2/"))).isTrue();
        assertThat(exclusions.contains(product("BUDS-PRO 2", "sku-other", "https://qcystore.co.kr/product/model/2/"))).isTrue();
        assertThat(exclusions.containsUrl("https://qcystore.co.kr/product/이어폰/1/?tracking=x")).isTrue();
        assertThat(exclusions.contains(product("Buds Pro 3", "sku-3", "https://qcystore.co.kr/product/model/3/"))).isFalse();
        assertThat(exclusions.searchTerms()).contains("Buds Pro 2").doesNotContain("사용자 개인 입력");
    }
    @Test void queryHintsStayBoundedWhileServerExclusionsKeepEntireHistory() {
        var previous = java.util.stream.IntStream.range(0, 20).mapToObj(i -> new CandidateSuggestion(UUID.randomUUID(), "Model " + i,
                "https://qcystore.co.kr/product/model/" + (i + 1) + "/", "", "test", Instant.now())).toList();
        var excluded = DiscoveryExclusions.from(previous, List.of());
        assertThat(excluded.publicNames()).hasSize(20);
        assertThat(excluded.contains(product("Model 0", "other", "https://qcystore.co.kr/product/model/100/"))).isTrue();
        assertThat(excluded.searchTerms()).isEqualTo(" -\"Model 15\" -\"Model 16\" -\"Model 17\" -\"Model 18\" -\"Model 19\"");
    }
    @Test void priorOfficialQcyIdentityExcludesANewColorPageWithoutRewritingHistory() {
        var old = new CandidateSuggestion(UUID.randomUUID(), "QCY HT16 블랙", "https://qcystore.co.kr/product/model/990/", "", "test", Instant.now());
        old.officialProduct(product(old.name(), "qcy-kr:cafe24_ylintlshop_1_990", old.url()));
        var white = product("QCY HT16 화이트", DiscoveryExclusions.qcyColorIdentity("QCY HT16 화이트"), "https://qcystore.co.kr/product/model/979/");
        assertThat(DiscoveryExclusions.from(List.of(old), List.of()).contains(white)).isTrue();
        assertThat(old.productIdentity()).isEqualTo("qcy-kr:cafe24_ylintlshop_1_990");
        var unverified = new CandidateSuggestion(UUID.randomUUID(), old.name(), old.url(), "", "test", Instant.now());
        assertThat(DiscoveryExclusions.from(List.of(unverified), List.of()).contains(white)).isFalse();
        assertThat(DiscoveryExclusions.from(List.of(old), List.of()).contains(product("QCY HT19 화이트", DiscoveryExclusions.qcyColorIdentity("QCY HT19 화이트"), white.offer().purchaseUrl()))).isFalse();
    }
    private OfficialProduct product(String name, String id, String url) {
        return new OfficialProduct(name, id, new OfficialOffer(url, "store", new java.math.BigDecimal("10000"), "KRW", null, Instant.now(), "", OfficialStorePolicy.VERSION), "");
    }
}
