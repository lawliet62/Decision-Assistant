package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AudioTechnicaProductVerifierTest {
    private static String fixture() {
        var colors = List.of("블랙", "그레이", "포레스트그린", "카페라테");
        var variants = new ArrayList<String>(); var options = new StringBuilder("<option value='*'>선택</option>");
        for (int i = 0; i < colors.size(); i++) {
            var color = colors.get(i); var code = "P0000BKT000" + (char) ('A' + i);
            variants.add("""
                {"@type":"Product","name":"ATH-SQ1TW2NC %s","color":"%s","sku":"cafe24_audiotechnica_1_955_%s",
                 "offers":{"@type":"Offer","price":139000,"priceCurrency":"KRW","availability":"https://schema.org/InStock",
                 "url":"https://www.audio-technica.kr/product/ath-sq1tw2nc/955/?item_code=%s"}}
                """.formatted(color, color, code, code));
            options.append("<option value='").append(color).append("'>").append(color).append("</option>");
        }
        return "<link rel='canonical' href='" + AudioTechnicaProductVerifier.URL + "'><script type='application/ld+json'>"
                + "{\"@type\":\"ProductGroup\",\"@id\":\"https://www.audio-technica.kr/product/ath-sq1tw2nc/955/\",\"name\":\"ATH-SQ1TW2NC\",\"productGroupID\":\"cafe24_audiotechnica_1_955\",\"variesBy\":[\"color\"],\"hasVariant\":["
                + String.join(",", variants) + "]}</script>"
                + "<div class='xans-product-detail'><div class='detail_name'><p>노이즈캔슬링 소형 초경량 디자인</p><h2>ATH-SQ1TW2NC</h2></div>"
                + "<div class='detail_price'><span class='product_price'>139,000원</span><span class='product_sale_price'></span></div>"
                + "<select product_type='product_option' option_title='색상'>" + options + "</select>"
                + "<div class='xans-product-action'><a class='btnSubmit' href='#none'><span id='actionBuy'>바로구매</span></a></div></div>";
    }
    private static OfficialProductParser.Result inspect(String html) {
        var url = AudioTechnicaProductVerifier.URL;
        return OfficialProductParser.inspect(url, new WebEvidenceProvider.Page("", "", html, url), Instant.now(), true);
    }
    @Test void onlyAcceptsSameModelStockOptionsAndUnconditionalDisplayedPrice() {
        var html = fixture();
        assertThat(inspect(html).product().offer().price()).isEqualByComparingTo("139000");
        var responsive = html.replace("<div class='xans-product-action'><a", "<div class='xans-product-action display_pc_only'><a")
                .replace("</a></div></div>", "</a></div><div class='xans-product-action gFixed'><a class='btnSubmit' href='#none'><span id='actionBuy'>바로구매</span></a></div></div>");
        assertThat(inspect(responsive).product()).isNotNull();
        assertThat(inspect(responsive.replace("gFixed", "other")).product()).isNull();
        assertThat(inspect(html.replace("https://www.audio-technica.kr/product/ath-sq1tw2nc/955/", AudioTechnicaProductVerifier.URL)).product()).isNotNull();
        assertThat(inspect(html.replaceFirst("https://www.audio-technica.kr/product/ath-sq1tw2nc/955/", AudioTechnicaProductVerifier.URL)).product()).isNull();
        for (var bad : List.of(html.replaceFirst("139000", "129000"), html.replace("139,000원", "129,000원"),
                html.replaceFirst("InStock", "OutOfStock"), html.replaceFirst("P0000BKT000A", "P0000BKT000Z"),
                html.replace("value='그레이'", "value='그레이' disabled"), html.replace("class='btnSubmit'", "class='btnSubmit displaynone'"),
                html.replace("class='btnSubmit'", "class='btnSubmit' aria-disabled='true'"), html.replace("href='#none'", "href='https://untrusted.example/'"),
                html.replace("product_sale_price'></span>", "product_sale_price'>110,000원</span>"),
                html.replace("\"priceCurrency\":\"KRW\"", "\"priceCurrency\":\"KRW\",\"eligibleCustomerType\":\"member\"")))
            assertThat(inspect(bad).product()).isNull();
        assertThat(OfficialStorePolicy.store(AudioTechnicaProductVerifier.URL + "?extra=1")).isEmpty();
    }
    @Test void specConfirmationRequiresExactModelAndDescription() {
        var html = "<title>完全ワイヤレスイヤホン『ATH-SQ1TW2NC』が登場｜オーディオテクニカ</title>"
                + "<span class='style_bold'>" + AudioTechnicaSpecificationRefreshWorker.QUOTE + "</span>";
        assertThat(AudioTechnicaSpecificationRefreshWorker.matches(html)).isTrue();
        assertThat(AudioTechnicaSpecificationRefreshWorker.matches(html.replace(AudioTechnicaSpecificationRefreshWorker.QUOTE, "通話用"))).isFalse();
        assertThat(AudioTechnicaSpecificationRefreshWorker.matches(html.replace("ATH-SQ1TW2NC", "ATH-SQ1TW2"))).isFalse();
        assertThat(AudioTechnicaSpecificationRefreshWorker.matches(html.replace("</title>", "</title><title>other</title>"))).isFalse();
        assertThat(AudioTechnicaSpecificationRefreshWorker.matches(html + "<span class='style_bold'>" + AudioTechnicaSpecificationRefreshWorker.QUOTE + "</span>")).isFalse();
    }
}
