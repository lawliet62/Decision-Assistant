package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

public class JblTuneBeam2VerifierTest {
    // 실제 DOM의 검증 필드만 재구성한 fixture이며 실제 공급자 응답은 아니다.
    public static final String HTML = """
            <title>JBL TUNE BEAM 2 어댑티브 노이즈 캔슬링 무선 이어폰 | JBLTBEAM2BLKAS | Samsung 대한민국</title>
            <link rel='canonical' href='%s'>
            <script type='application/ld+json'>{"@type":"Product","@id":"%s","name":"%s","sku":"%s",
              "offers":{"@type":"Offer","url":"%s","priceCurrency":"KRW","availability":"https://schema.org/InStock","price":99000}}</script>
            <div id='goodsRevampOrderSummary'><h4 class='prod-title'>%s</h4><p class='prod-sku'>%s</p>
              <div class='summary-price-list'><dl class='event-price'><dt>기준가</dt><dd><span data-price='99000'>99,000</span> 원</dd></dl></div>
              <div>최대 혜택가와 카드 안내는 기준가 증거로 사용하지 않는다.</div><strong id='finalPrc'>99,000</strong><strong class='pd-total-price' name='totalPrice'>99,000</strong>
              <button id='btnDirect' data-cta-type='normal'>구매하기</button></div>
            <input id='countPrd' value='1' max='186'>
            """.formatted(JblTuneBeam2Verifier.URL, JblTuneBeam2Verifier.URL, JblTuneBeam2Verifier.NAME,
                    JblTuneBeam2Verifier.SKU, JblTuneBeam2Verifier.URL, JblTuneBeam2Verifier.NAME,
                    JblTuneBeam2Verifier.SKU);
    public static final String SPEC_HTML = "<title>" + JblTuneBeam2Verifier.SPEC_TITLE + "</title><link rel='canonical' href='"
            + JblTuneBeam2Verifier.SPEC_URL + "'><h1 itemprop='name'>JBL Tune Beam 2</h1><div class='feature-slide'><p>"
            + JblTuneBeam2Verifier.ANC_QUOTE + "</p><p>" + JblTuneBeam2Verifier.ANDROID_QUOTE + "</p></div>";

    @Test void requiresOneSkuPublicBasePriceAndMatchingPurchaseAmount() {
        assertThat(JblTuneBeam2Verifier.price(HTML)).contains(new java.math.BigDecimal("99000"));
        assertThat(JblTuneBeam2Verifier.price(HTML + "<script type='application/ld+json'>{\"@type\":[\"WebPage\",\"ItemPage\"]}</script>"))
                .contains(new java.math.BigDecimal("99000"));
        for (var invalid : List.of(HTML.replace("value='1'", "value='2'"),
                HTML.replace("data-price='99000'", "data-price='89000'"), HTML.replace("99,000</span>", "89,000</span>"),
                HTML.replace("기준가</dt>", "회원가</dt>"), HTML.replace("id='finalPrc'>99,000", "id='finalPrc'>89,000"),
                HTML.replace("name='totalPrice'>99,000", "name='totalPrice'>89,000"),
                HTML.replace("id='btnDirect'", "id='btnDirect' disabled"), HTML.replace("id='btnDirect'", "id='btnDirect' style='display:none'"),
                HTML.replace("schema.org/InStock", "schema.org/OutOfStock"), HTML.replace("JBLTBEAM2BLKAS", "JBLTBEAM2WHTAS"),
                HTML.replace("max='186'", "max='0'"), HTML.replace("\"price\":99000", "\"eligibleCustomerType\":\"Member\",\"price\":99000"),
                HTML + "<input id='countPrd' value='1'>", HTML.replace(".com/sec/", ".com/other/")))
            assertThat(JblTuneBeam2Verifier.price(invalid)).isEmpty();
    }

    @Test void limitedToRegisteredCatalogUrl() {
        var page = new WebEvidenceProvider.Page("", "", HTML, JblTuneBeam2Verifier.URL);
        assertThat(OfficialProductParser.inspect(JblTuneBeam2Verifier.URL, page, Instant.now(), true).product().identity()).isEqualTo(JblTuneBeam2Verifier.IDENTITY);
        assertThat(OfficialProductParser.inspect(JblTuneBeam2Verifier.URL, page, Instant.now(), false).product()).isNull();
        assertThat(OfficialStorePolicy.store(JblTuneBeam2Verifier.URL + "?member=true")).isEmpty();
        assertThat(OfficialStorePolicy.store(JblTuneBeam2Verifier.URL.replace("BLKAS", "WHTAS"))).isEmpty();
    }

    @Test void requiresModelCanonicalAndQuoteInsideFeatureParagraph() {
        var fact = new DiscoveryContracts.ProductFact("ANC 지원", "지원", JblTuneBeam2Verifier.SPEC_URL,
                JblTuneBeam2Verifier.ANC_QUOTE, Instant.now(), "UNREVIEWED");
        assertThat(JblTuneBeam2Verifier.specification(SPEC_HTML, fact)).isTrue();
        for (var invalid : List.of(SPEC_HTML.replace("JBL Tune Beam 2", "JBL Tune Beam 3"),
                SPEC_HTML.replace("feature-slide", "related-product"), SPEC_HTML.replace("</p>", " 미지원</p>"), SPEC_HTML.replace("<p>", "<script>").replace("</p>", "</script>"),
                SPEC_HTML.replace("rel='canonical'", "rel='alternate'")))
            assertThat(JblTuneBeam2Verifier.specification(invalid, fact)).isFalse();
    }
}
