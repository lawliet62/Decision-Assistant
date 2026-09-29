package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static com.decisionassistant.decision.DecisionTypes.TemplateKey.*;
import static org.assertj.core.api.Assertions.*;

class DiscoveryVerificationTest {
    private static final String URL = "https://www.lge.co.kr/product/notebook/model8";
    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");
    private static final String DATA = """
            {"@type":"Product","name":"Laptop A","sku":"model8","offers":{"@type":"Offer","priceCurrency":"KRW","price":1290000}}
            """;
    private static final String PRICE = "<tr><th>판매가</th><td>1,290,000원</td></tr>";
    private String document(String rows) {
        return "<script type='application/ld+json'>" + DATA + "</script><article itemscope itemtype='https://schema.org/Product'><h1 itemprop='name'>Laptop A</h1><table>" + rows + "</table></article>";
    }
    private OfficialProductParser.Result inspect(String html) {
        return OfficialProductParser.inspect(URL, new WebEvidenceProvider.Page("Laptop A", "Laptop A", html, URL), NOW);
    }
    private List<RequirementCheck> checks(List<ProductFact> facts) {
        return MinimumRequirementMatcher.check(com.decisionassistant.template.MinimumRequirements.forCategory(LAPTOP), Map.of("memory", "16", "storage", "512"), facts);
    }
    private OfficialOffer offer(String version) {
        return new OfficialOffer(URL, "LG전자", new BigDecimal("1290000"), "KRW", null, NOW, "판매가", version);
    }
    @Test void memberLabelOverridesUnconditionalLookingStructuredOfferWithoutSubstitutingListPrice() {
        var result = inspect(document("<tr><th>할인 전 판매가</th><td>1,550,000원</td></tr><tr><th>회원할인가</th><td>1,290,000원</td></tr>"));
        assertThat(result.product()).isNull();
        assertThat(result.rejection()).isEqualTo(OfficialProductParser.Rejection.CONDITIONAL_PRICE);
        assertThat(inspect(document("")).rejection()).isEqualTo(OfficialProductParser.Rejection.PRICE_CONTEXT_MISSING);
    }
    @Test void unrelatedMembershipAndSeparateDiscountDoNotInvalidateNormalSalePrice() {
        var html = document(PRICE + "<tr><th>회원할인가</th><td>1,190,000원</td></tr>") + "<nav>회원 가격 안내 1,290,000원</nav>";
        var result = inspect(html);
        assertThat(result.rejection()).isNull();
        assertThat(result.product().priceQuote()).isEqualTo("판매가 1,290,000원");
    }
    @Test void priceFromAnotherProductOrAmbiguousPriceRowCannotVerifyOffer() {
        assertThat(inspect(document(PRICE).replace("itemprop='name'>Laptop A", "itemprop='name'>Laptop B")).rejection())
                .isEqualTo(OfficialProductParser.Rejection.PRICE_CONTEXT_MISSING);
        assertThat(inspect(document("<tr><th>판매가</th><td>1,290,000원 ~ 1,500,000원</td></tr>")).product()).isNull();
    }
    @Test void observedLgPricingStructureRejectsMemberPrice() {
        var html = "<title>Laptop A | model8</title><link rel='canonical' href='" + URL + "'><script type='application/ld+json'>" + DATA + "</script>"
                + "<div class='pricing_info_box'><div class='PdpPcPricingInfo_pricing_info_item_inner__test'><div>회원할인가</div><p>할인 적용 후 가격 1,290,000원</p></div></div>";
        assertThat(inspect(html).rejection()).isEqualTo(OfficialProductParser.Rejection.CONDITIONAL_PRICE);
    }
    @Test void observedQcyPurchaseTableVerifiesExactPrice() {
        var url = "https://qcystore.co.kr/product/detail.html?product_no=633";
        var html = "<title>Laptop A</title><script type='application/ld+json'>" + DATA + "</script>"
                + "<div class='xans-product-detail'><table><tr class='product_price_css'><th>판매가</th><td>1,290,000원</td></tr></table></div>";
        assertThat(OfficialProductParser.inspect(url, new WebEvidenceProvider.Page("", "", html, url), NOW).product()).isNotNull();
    }
    @Test void splitUnitAndInstalledCapacityWithExpansionSlotAreBelowWhileQuoteIsPreserved() {
        var html = document(PRICE) + "<title>Laptop A | model8</title><div id='pdp-pc-specs-more'><dl>"
                + "<div><dt>메모리 용량 (GB)</dt><dd>8</dd></div><div><dt>SSD</dt><dd>256 GB (NVMe™) + 확장 슬롯 1 (PCIe 4.0)</dd></div></dl></div>";
        var blocks = inspect(html).blocks();
        var facts = blocks.stream().map(b -> SpecNormalizer.fact(b, URL, NOW)).toList();
        assertThat(facts).extracting(ProductFact::value).containsExactly("8 GB", "256 GB");
        assertThat(facts.getLast().quote()).contains("확장 슬롯 1");
        assertThat(checks(facts)).extracting(RequirementCheck::status).containsOnly(RequirementStatus.BELOW);
        var legacy = new ProductFact("저장공간", "256 GB", URL, "SSD 256 GB + 확장 슬롯 1", NOW, "UNREVIEWED");
        assertThat(checks(List.of(legacy))).allMatch(c -> c.status() == RequirementStatus.UNKNOWN);
    }
    @Test void maximumOptionAndConflictingCapacitiesRemainUnknown() {
        for (var raw : List.of("최대 32GB", "8GB 또는 32GB", "8GB (최대 32GB)", "8/32GB", "32GB (선택)")) {
            var fact = SpecNormalizer.fact(new EvidenceBlock("b0", "model8", "메모리", "메모리", raw, "메모리: " + raw), URL, NOW);
            assertThat(checks(List.of(fact)).getFirst().status()).isEqualTo(RequirementStatus.UNKNOWN);
        }
        var blocks = inspect(document(PRICE + "<tr><th>메모리</th><td>8 GB</td></tr><tr><th>메모리</th><td>32 GB</td></tr>")).blocks();
        assertThat(checks(blocks.stream().map(b -> SpecNormalizer.fact(b, URL, NOW)).toList()).getFirst().unknownReason()).isEqualTo(UnknownReason.CONFLICT);
    }
    @Test void productBlocksExcludeReviewsRelatedProductsAndOtherConfigurations() {
        var html = document(PRICE + "<tr><th>메모리 용량 (GB)</th><td>8</td></tr><tr class='review'><th>메모리</th><td>64GB</td></tr>")
                + "<aside itemscope itemtype='https://schema.org/Product'><h2 itemprop='name'>Laptop B</h2><dl><dt>메모리</dt><dd>32GB</dd></dl></aside>";
        assertThat(inspect(html).blocks()).extracting(EvidenceBlock::value).containsExactly("8");
    }
    @Test void structuredPropertiesRetainTheirUnits() {
        var node = JsonMapper.builder().build().readTree("{\"additionalProperty\":[{\"name\":\"RAM\",\"value\":8,\"unitText\":\"GB\"}]}");
        var blocks = ProductEvidence.extract(Jsoup.parse(""), node, URL, "Laptop A", "model8");
        assertThat(SpecNormalizer.fact(blocks.getFirst(), URL, NOW).value()).isEqualTo("8 GB");
    }
    @Test void equalFamilyNamesDoNotBindDifferentSkuOrProductUrl() {
        assertThat(inspect(document(PRICE).replace("</h1>", "</h1><span itemprop='sku'>model32</span>")).product()).isNull();
        assertThat(inspect(document(PRICE).replace("</h1>", "</h1><a itemprop='url' href='/product/notebook/model32'>구매</a>")).product()).isNull();
        assertThat(SpecNormalizer.fieldMatches("메모리", "32GB", "Laptop A VRAM 32GB")).isFalse();
        assertThat(SpecNormalizer.fieldMatches("메모리", "512GB", "Laptop A SSD 512GB RAM 8GB")).isFalse();
        assertThat(SpecNormalizer.fieldMatches("메모리", "8GB", "Laptop A SSD 512GB RAM 8GB")).isTrue();
        assertThat(SpecNormalizer.fieldMatches("메모리", "8GB", "Laptop A 8GB RAM")).isTrue();
    }
    @Test void priceIdentityIgnoresWhitespaceFormattingWithoutChangingProductModel() {
        var html = document(PRICE).replace("\"Laptop A\"", "\"Laptop  A\"");
        assertThat(inspect(html).product()).isNotNull();
    }
    @Test void serverRetainsKnownFactsWhenAiOmitsThemAndRejectsWrongBlockField() {
        var inspected = inspect(document(PRICE + "<tr><th>메모리 용량 (GB)</th><td>8</td></tr>"));
        var page = new SourcePage(URL, "Laptop A", "# Laptop A", NOW, inspected.product(), inspected.blocks());
        var input = new DiscoveryInput("노트북", List.of("사무"), null, "Windows", List.of("메모리", "저장공간", "가격"), List.of(page), List.of());
        var block = inspected.blocks().getFirst();
        var proposal = new DiscoveryProposal(List.of(new ProposedProduct("Laptop A", URL, "Laptop A", "무조건 만족합니다.",
                List.of(new ProposedFact("저장공간", "8", URL, block.quote(), block.id())))));
        var result = DiscoveryWorker.validateOfficialProducts(UUID.randomUUID(), proposal, input).getFirst();
        assertThat(result.facts()).extracting(ProductFact::name).containsExactly("가격", "메모리");
        assertThat(result.facts().getLast().value()).isEqualTo("8 GB");
        assertThat(result.reason()).doesNotContain("만족");
    }
    private ProductFact fact(String name, String value, String quote) { return new ProductFact(name, value, URL, quote, NOW, "UNREVIEWED"); }
    @Test void basicCompatibilityRequiresEvidenceAndDoesNotUseAppSupportOrBluetoothAlone() {
        for (var facts : List.of(List.<ProductFact>of(), List.of(fact("전용 앱", "iOS 12 이상", "전용 앱: iOS 12 이상")),
                List.of(fact("호환성", "iOS 12 이상", "전용 앱 iOS 12 이상 지원")), List.of(fact("호환성", "Bluetooth 5.3", "Bluetooth 5.3 지원")))) {
            var check = CompatibilityMatcher.check(EARBUDS, "iOS", facts, offer(OfficialStorePolicy.VERSION));
            assertThat(check.status()).isEqualTo(RequirementStatus.UNKNOWN);
            assertThat(DiscoveryRanking.group(offer(OfficialStorePolicy.VERSION), List.of(), null, check)).isEqualTo(DiscoveryRanking.Group.UNKNOWN);
        }
        var both = List.of(fact("호환성", "Android, iOS", "호환성: Android, iOS"));
        assertThat(CompatibilityMatcher.check(EARBUDS, "iOS", both, offer(OfficialStorePolicy.VERSION)).status()).isEqualTo(RequirementStatus.MEETS);
        assertThat(CompatibilityMatcher.check(EARBUDS, "iOS", List.of(fact("호환성", "Android 지원", "Android 지원")), offer(OfficialStorePolicy.VERSION)).status()).isEqualTo(RequirementStatus.UNKNOWN);
        assertThat(CompatibilityMatcher.check(EARBUDS, "iOS", List.of(fact("호환성", "iOS 미지원", "iOS 미지원")), offer(OfficialStorePolicy.VERSION)).status()).isEqualTo(RequirementStatus.BELOW);
        assertThat(CompatibilityMatcher.check(EARBUDS, "iOS", List.of(), offer("official-kr-v1"))).isNull();
    }
    @Test void platformAndMonitorChecksRespectTheirDifferentMeanings() {
        assertThat(CompatibilityMatcher.check(PHONE, "iOS", List.of(fact("OS", "Android 16", "운영체제: Android 16")), offer(OfficialStorePolicy.VERSION)).status()).isEqualTo(RequirementStatus.BELOW);
        assertThat(CompatibilityMatcher.check(LAPTOP, "Windows", List.of(fact("OS", "Windows 11 Home", "운영체제: Windows 11 Home")), offer(OfficialStorePolicy.VERSION)).status()).isEqualTo(RequirementStatus.MEETS);
        assertThat(CompatibilityMatcher.check(MONITOR, "USB-C", List.of(fact("연결 단자", "USB-C", "연결 단자: USB-C")), offer(OfficialStorePolicy.VERSION)).status()).isEqualTo(RequirementStatus.UNKNOWN);
        assertThat(CompatibilityMatcher.check(MONITOR, "USB-C", List.of(fact("연결 단자", "USB-C 영상 입력", "영상 입력: USB-C")), offer(OfficialStorePolicy.VERSION)).status()).isEqualTo(RequirementStatus.MEETS);
    }
    @Test void optionalOrMultipleOperatingSystemsDoNotProveTheProvidedOperatingSystem() {
        for (var value : List.of("Windows 또는 macOS", "Windows / Android", "FreeDOS, Windows 별도 설치", "Windows 11 (선택)")) {
            assertThat(CompatibilityMatcher.check(LAPTOP, "Windows", List.of(fact("OS", value, "운영체제: " + value)),
                    offer(OfficialStorePolicy.VERSION)).status()).isEqualTo(RequirementStatus.UNKNOWN);
        }
    }
    @Test void explanationUsesUnknownAndBelowResultsInsteadOfInventedSuitability() {
        var input = new DiscoveryInput("무선 이어폰", List.of("출퇴근", "운동", "통화", "음악"), new BigDecimal("1500000"), "Android", List.of(), List.of(), List.of());
        var check = new RequirementCheck("anc", "ANC 지원", "지원 필수", RequirementStatus.UNKNOWN, List.of(), UnknownReason.MISSING);
        var compatibility = CompatibilityMatcher.check(EARBUDS, "Android", List.of(), offer(OfficialStorePolicy.VERSION));
        var reason = DiscoveryExplanation.explain(input, offer(OfficialStorePolicy.VERSION), List.of(), List.of(check), compatibility);
        assertThat(reason).contains("ANC 지원 여부를", "확인하지 못했습니다", "Android", "착용 안정성").doesNotContain("적합", "ANC를 지원");
        var laptop = DiscoveryExplanation.explain(input, offer(OfficialStorePolicy.VERSION), List.of(),
                List.of(new RequirementCheck("memory", "메모리", "16GB 이상", RequirementStatus.BELOW, List.of(fact("메모리", "8GB", "RAM 8GB")), null)), null);
        assertThat(laptop).contains("미달", "8GB", "16GB");
    }
}
