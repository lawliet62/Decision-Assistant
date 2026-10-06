package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static org.assertj.core.api.Assertions.*;

class OfficialProductLinkingTest {
    private static final String URL = "https://qcystore.co.kr/product/detail.html?product_no=990";
    private static final String NAME = "QCY HT16 멜로버즈 블랙";
    private String variant(String color, int price) {
        return """
                {"@type":"Product","name":"%s %s","sku":"variant-%s",
                 "additionalProperty":[{"@type":"PropertyValue","name":"선택","value":"%s"}],
                 "offers":{"@type":"Offer","url":"%s&item_code=%s","priceCurrency":"KRW","price":%d,"availability":"https://schema.org/InStock"}}
                """.formatted(NAME, color, color, color, URL, color, price);
    }
    private String group() {
        return """
                {"@type":"ProductGroup","name":"%s","productGroupID":"page-990","variesBy":["선택"],"hasVariant":[%s,%s]}
                """.formatted(NAME, variant("블랙", 31800), variant("화이트", 31800));
    }
    private String html(String data) {
        return "<title>" + NAME + "</title><script type='application/ld+json'>" + data + "</script>"
                + "<div class='xans-product-detail'><table><tr class='product_price_css'><th>판매가</th><td>31,800원</td></tr></table></div>";
    }
    private OfficialProductParser.Result inspect(String html) {
        return OfficialProductParser.inspect(URL, new WebEvidenceProvider.Page("", "", html, URL), Instant.now());
    }
    @Test void acceptsObservedQcySelectionOnlyWhenEveryVariantIsExplicitlyTheSameProductsColor() {
        var result = inspect(html(group()));
        assertThat(result.rejection()).isNull();
        assertThat(result.product().offer().price()).isEqualByComparingTo("31800");
        for (var unsupported : List.of(
                group().replace("화이트", "256GB"), group().replace("화이트", "화이트 + 케이스"),
                group().replace("\"variesBy\":[\"선택\"]", "\"variesBy\":[\"선택\",\"size\"]"),
                group().replace("\"value\":\"화이트\"", "\"value\":\"\""),
                group().replace(NAME + " 화이트", "QCY 다른 모델 화이트"),
                group().replace("\"additionalProperty\":[", "\"additionalProperty\":[{\"name\":\"용량\",\"value\":\"256GB\"},")))
            assertThat(inspect(html(unsupported)).rejection()).isEqualTo(OfficialProductParser.Rejection.VARIANT_UNSUPPORTED);
        var otherStore = "https://www.samsung.com/sec/buds/model/sku/";
        assertThat(OfficialProductParser.inspect(otherStore, new WebEvidenceProvider.Page("", "", html(group()), otherStore), Instant.now()).rejection())
                .isEqualTo(OfficialProductParser.Rejection.VARIANT_UNSUPPORTED);
    }
    @Test void recognizedColorsStillRequireEqualUnconditionalAvailableAndVisiblePrices() {
        assertThat(inspect(html(group().replaceFirst("31800", "32800"))).rejection()).isEqualTo(OfficialProductParser.Rejection.PRICE_AMBIGUOUS);
        assertThat(inspect(html(group().replace("/InStock", "/OutOfStock"))).rejection()).isEqualTo(OfficialProductParser.Rejection.UNAVAILABLE);
        assertThat(inspect(html(group()).replace("판매가", "회원할인가")).rejection()).isEqualTo(OfficialProductParser.Rejection.CONDITIONAL_PRICE);
        assertThat(inspect(html(group()).replace("31,800원", "32,800원")).rejection()).isEqualTo(OfficialProductParser.Rejection.PRICE_CONTEXT_MISSING);
    }
    @Test void separateColorLandingPagesDoNotCountAsTwoModels() {
        var black = inspect(html(group())).product();
        var white = inspect(html(group()).replace(NAME, "QCY HT16 멜로버즈 화이트").replace("page-990", "page-979")).product();
        var otherModel = inspect(html(group()).replace("HT16", "HT19")).product();
        assertThat(white.identity()).isEqualTo(black.identity());
        assertThat(otherModel.identity()).isNotEqualTo(black.identity());
        var exclusions = new DiscoveryExclusions(java.util.Set.of(black.identity()), java.util.Set.of(), List.of(), List.of());
        assertThat(exclusions.contains(white)).isTrue();
    }
    @Test void lgLegacyAndCurrentPathsAreAliasesOnlyForTheSameCategoryAndExactModel() {
        var old = "https://www.lge.co.kr/notebook/model-a";
        var current = "https://www.lge.co.kr/product/notebook/model-a";
        assertThat(OfficialProductParser.samePage(old, current)).isTrue();
        assertThat(OfficialProductParser.samePage(current + "/", old)).isTrue();
        for (var different : List.of(current.replace("model-a", "model-b"), current.replace("notebook", "monitors"),
                current.replace("https", "http"), current.replace("lge.co.kr", "lge.co.kr.evil.example"),
                "https://www.lge.co.kr/product/notebook/%2e%2e/model-a"))
            assertThat(OfficialProductParser.samePage(old, different)).isFalse();
        var page = """
                <script type='application/ld+json'>
                {"@type":"Product","name":"Laptop A","sku":"model-a","url":"%s",
                 "offers":{"@type":"Offer","url":"%s","price":31800,"priceCurrency":"KRW"}}
                </script><article itemscope itemtype='https://schema.org/Product'><h1 itemprop='name'>Laptop A</h1>
                <table><tr><th>판매가</th><td>31,800원</td></tr></table></article>
                """.formatted(current, current);
        assertThat(OfficialProductParser.inspect(old, new WebEvidenceProvider.Page("", "", page, current), Instant.now()).product()).isNotNull();
        var wrongCanonical = "<link rel='canonical' href='https://www.lge.co.kr/product/notebook/model-b'>";
        assertThat(OfficialProductParser.inspect(old, new WebEvidenceProvider.Page("", "", wrongCanonical + page, current), Instant.now()).rejection())
                .isEqualTo(OfficialProductParser.Rejection.CANONICAL_URL_MISMATCH);
    }
    @Test void finalCandidatesDeduplicateVerifiedModelWithoutCombiningFactsAcrossColorPages() {
        var black = inspect(html(group())).product();
        var whiteUrl = URL.replace("990", "979");
        var whiteHtml = html(group()).replace(NAME, "QCY HT16 멜로버즈 화이트").replace(URL, whiteUrl);
        var white = OfficialProductParser.inspect(whiteUrl, new WebEvidenceProvider.Page("", "", whiteHtml, whiteUrl), Instant.now()).product();
        var pages = List.of(new SourcePage(URL, black.name(), black.name(), Instant.now(), black, List.of()),
                new SourcePage(whiteUrl, white.name(), white.name(), Instant.now(), white, List.of()));
        var input = new DiscoveryInput("무선 이어폰", List.of(), null, null, List.of("가격"), pages, List.of());
        var proposal = new DiscoveryProposal(List.of(
                new ProposedProduct(black.name(), URL, black.name(), "", List.of()),
                new ProposedProduct(white.name(), whiteUrl, white.name(), "", List.of())));
        var candidates = DiscoveryWorker.validateOfficialProducts(java.util.UUID.randomUUID(), proposal, input);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().productIdentity()).isEqualTo(black.identity());
        assertThat(candidates.getFirst().facts()).allMatch(fact -> fact.sourceUrl().equals(candidates.getFirst().url()));
    }
}
