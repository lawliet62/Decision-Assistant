package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static com.decisionassistant.decision.DecisionTypes.TemplateKey.EARBUDS;
import static org.assertj.core.api.Assertions.*;

class OfficialSpecificationTest {
    static final String PURCHASE = "https://qcystore.co.kr/product/detail.html?product_no=633";
    static final String DOCUMENT = "https://www.qcy.com/products/qcy-t13-anc";
    static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
    static OfficialProduct product(String code, String url, String identity, int price) {
        var name = "QCY " + code + " 블루투스 이어폰";
        return new OfficialProduct(name, identity, new OfficialOffer(url, "QCY 공식몰", BigDecimal.valueOf(price), "KRW", null,
                NOW, "판매가", OfficialStorePolicy.VERSION), "", "판매가 " + price, OfficialSpecificationPolicy.purchaseModel(url, name));
    }
    private final OfficialProduct product = product("T13 ANC", PURCHASE, "qcy:t13anc", 29900);
    static String html(String heading, String contents) { return "<main><h1>" + heading + "</h1>" + contents + "</main>"; }
    private OfficialSpecificationParser.Result inspect(String html) {
        return OfficialSpecificationParser.inspect(DOCUMENT, new WebEvidenceProvider.Page("", "", html, DOCUMENT), product,
                List.of("호환성", "ANC 지원", "전용 앱"), NOW);
    }
    @Test void observedQcySpecificationLinesKeepModelProofSourceAndCompatibleSystemSeparateFromApp() {
        var html = html("QCY T13 ANC True Wireless Earbuds, 30H Playtime", """
                <div class="specification-section"><div class="specification-content">
                  <h3>Basic</h3><div class="spec-details"><p>Product Name: QCY T13 ANC</p>
                  <p>Model Number: BH22DT10A<br>Compatible system: iOS/Android<br>Dimensions: 21.85×32.63×24.05mm</p></div>
                </div><div class="specification-content"><h3>Noise Canceling</h3><div class="spec-details">
                  <p>Active Noise Cancellation (ANC)<br>Environment Noise Cancellation (ENC)<br>Wind Noise Reduction</p>
                </div></div><dl><dt>App compatibility</dt><dd>iOS 12/Android 8</dd></dl></div>
                <section class="featured-collection"><h3>QCY HT16</h3><p>Compatible system: Windows</p></section>
                """);
        var result = inspect(html);
        assertThat(result.outcome()).isEqualTo("ACCEPTED");
        assertThat(result.facts()).extracting(ProductFact::name).containsExactlyInAnyOrder("호환성", "ANC 지원", "전용 앱");
        assertThat(result.facts()).allSatisfy(fact -> {
            assertThat(fact.sourceUrl()).isEqualTo(DOCUMENT);
            assertThat(fact.officialEvidence().model()).isEqualTo("T13ANC");
            assertThat(fact.officialEvidence().purchaseModelQuote()).isEqualTo(product.name());
            assertThat(fact.officialEvidence().documentModelQuote()).contains("T13 ANC");
            assertThat(fact.officialEvidence().contentHash()).matches("[a-f0-9]{64}");
            assertThat(fact.verificationStatus()).isEqualTo("UNREVIEWED");
        });
        assertThat(CompatibilityMatcher.check(EARBUDS, "Android", result.facts(), product.offer()).status()).isEqualTo(RequirementStatus.MEETS);
        assertThat(MinimumRequirementMatcher.check(com.decisionassistant.template.MinimumRequirements.forCategory(EARBUDS),
                Map.of("anc", "required"), result.facts()).getFirst().status()).isEqualTo(RequirementStatus.MEETS);
    }
    @Test void appOsBluetoothAndTitleDoNotProveBasicCompatibilityOrAnc() {
        var facts = inspect(html("QCY T13 ANC", "<p>The QCY app is available on iOS and Android.</p><dl><dt>App compatibility</dt><dd>iOS/Android</dd></dl><p>Bluetooth: 5.3</p>")).facts();
        assertThat(facts).extracting(ProductFact::name).containsExactly("전용 앱");
        assertThat(CompatibilityMatcher.check(EARBUDS, "iOS", facts, product.offer()).status()).isEqualTo(RequirementStatus.UNKNOWN);
        assertThat(inspect(html("QCY T13 ANC", "<h2>QCY App</h2><dl><dt>Compatible system</dt><dd>iOS/Android</dd></dl>")).facts()).isEmpty();
        assertThat(inspect(html("QCY T13 ANC", "<div class='app-spec'><p>Compatible system: iOS/Android</p></div>")).facts()).isEmpty();
    }
    @Test void generationsSuffixesAndMultipleModelsAreNotInterchangeable() {
        for (var other : List.of("T13", "T13 ANC 2", "T13 ANC2", "T13 2", "T13 Pro", "T13ANC2", "T13 ANC+", "T13_ANC", "T13 ANC Ultra", "T13 ANC and HT16"))
            assertThat(inspect(html("QCY " + other, "<dl><dt>ANC</dt><dd>supported</dd></dl>")).facts()).as(other).isEmpty();
        for (var spelling : List.of("T13ANC2", "T13 ANC2", "T13 ANC 2"))
            assertThat(OfficialSpecificationPolicy.purchaseModel(PURCHASE, "QCY " + spelling).code()).isEqualTo("T13ANC2");
        assertThat(OfficialSpecificationPolicy.purchaseModel(PURCHASE, "QCY T13 2").code()).isEqualTo("T13_2");
        assertThat(OfficialSpecificationPolicy.matches(OfficialSpecificationPolicy.purchaseModel(PURCHASE, "QCY T13 2"), "QCY T132")).isFalse();
        assertThat(inspect(html("QCY T13 ANC", "<h2>QCY T13 ANC 2</h2><dl><dt>ANC</dt><dd>supported</dd></dl>")).outcome()).isEqualTo("MODEL_AMBIGUOUS");
        assertThat(inspect(html("QCY T13 ANC", "<h3>QCY HT16</h3><dl><dt>ANC</dt><dd>supported</dd></dl>")).outcome()).isEqualTo("MODEL_AMBIGUOUS");
        assertThat(OfficialSpecificationPolicy.purchaseModel(PURCHASE, "QCY T13 ANC / T13 ANC 2")).isNull();
    }
    @Test void unrelatedRegionsAndSearchTitleCannotSupplyModelOrSpecifications() {
        var contents = "<aside><h2>QCY T13 ANC</h2><dl><dt>ANC</dt><dd>supported</dd></dl></aside>";
        assertThat(inspect(html("Support", contents)).facts()).isEmpty();
        assertThat(inspect(html("QCY T13 ANC", "<section class='related'><dl><dt>ANC</dt><dd>supported</dd></dl></section>"
                + "<section class='review'><p>Compatible system: iOS/Android</p></section>")).facts()).isEmpty();
        var nested = inspect(html("QCY T13 ANC", "<article itemscope itemtype='https://schema.org/Product'><h1>QCY T13 ANC</h1><dl><dt>ANC</dt><dd>supported</dd></dl></article>"));
        assertThat(nested.facts()).hasSize(1);
        assertThat(inspect(html("QCY T13 ANC", "<div class='specification-section'><div class='spec-details'>"
                + "<p>Product Name: QCY HT16</p><p>Compatible system: iOS/Android</p></div></div>")).facts()).isEmpty();
    }
    @Test void unregisteredRedirectsCanonicalsAndDifferentSupportModelsAreRejected() {
        for (var other : List.of("https://www.qcy.com.evil.test/products/qcy-t13-anc", "https://support.qcy.com/support-center-t13-anc-2",
                "https://www.qcy.com/products/qcy-t13", "http://www.qcy.com/products/qcy-t13-anc", "https://www.qcy.com/products/../qcy-t13-anc")) {
            assertThat(OfficialSpecificationParser.inspect(DOCUMENT, new WebEvidenceProvider.Page("", "", html("QCY T13 ANC", ""), other), product, List.of("ANC 지원"), NOW).facts()).isEmpty();
        }
        assertThat(inspect("<link rel='canonical' href='https://www.qcy.com/products/qcy-t13'>" + html("QCY T13 ANC", "<dl><dt>ANC</dt><dd>supported</dd></dl>")).outcome()).isEqualTo("CANONICAL_URL_MISMATCH");
        assertThat(OfficialSpecificationPolicy.sameDocument("https://www.qcy.com/pages/support-center-t13-anc", "https://support.qcy.com/support-center-t13-anc", product.model())).isTrue();
        for (var url : List.of("https://www.qcy.com/products/%2e%2e/qcy-t13-anc", "https://www.qcy.com:8443/products/qcy-t13-anc", "https://user@www.qcy.com/products/qcy-t13-anc", "https://www.qcy.com/products/guide.pdf"))
            assertThat(OfficialSpecificationPolicy.allows(url, product.model())).isFalse();
    }
    @Test void verifiedSupplementConflictsStayUnknownAndNeverReplaceThePurchasePrice() {
        var page = new SourcePage(PURCHASE, product.name(), "# " + product.name(), NOW, product,
                List.of(new EvidenceBlock("b0", product.identity(), "ANC 지원", "ANC", "미지원", "ANC: 미지원")));
        var supplemental = inspect(html("QCY T13 ANC", "<dl><dt>ANC</dt><dd>supported</dd></dl>")).facts();
        var proposal = new DiscoveryProposal(List.of(new ProposedProduct(product.name(), PURCHASE, product.name(), List.of(
                new ProposedFact("ANC 지원", "supported", DOCUMENT, "ANC: supported")))));
        var input = new DiscoveryInput("무선 이어폰", List.of(), null, null, List.of("가격", "ANC 지원"), List.of(page), List.of());
        var candidate = DiscoveryWorker.validateOfficialProducts(UUID.randomUUID(), proposal, input, Map.of(PURCHASE, supplemental)).getFirst();
        assertThat(candidate.facts()).hasSize(3);
        assertThat(candidate.facts().getFirst().sourceUrl()).isEqualTo(PURCHASE);
        assertThat(candidate.officialOffer().price()).isEqualByComparingTo("29900");
        var check = MinimumRequirementMatcher.check(com.decisionassistant.template.MinimumRequirements.forCategory(EARBUDS), Map.of("anc", "required"), candidate.facts()).getFirst();
        assertThat(check.status()).isEqualTo(RequirementStatus.UNKNOWN);
        assertThat(check.unknownReason()).isEqualTo(UnknownReason.CONFLICT);
    }
}
