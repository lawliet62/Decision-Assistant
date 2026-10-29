package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class SennheiserProductVerifierTest {
    private static final String URL = "https://kr.sennheiser-hearing.com/product/detail.html?product_no=731";
    private static final String CANONICAL = "https://kr.sennheiser-hearing.com/product/모멘텀-트루-와이어리스-5/731/";
    private static String fixture() {
        var colors = new String[]{"블랙 그래파이트", "크림", "데님", "라벤더", "코퍼"};
        var variants = new java.util.ArrayList<String>();
        var options = new StringBuilder("<option value='*'>선택</option>");
        for (int i = 0; i < colors.length; i++) {
            var color = colors[i]; var code = "P0000BCD000" + (char)('B' + i);
            variants.add("""
                    {"@type":"Product","name":"모멘텀 트루 와이어리스 5 %s","color":"%s","sku":"cafe24_bnispace_1_731_%s",
                    "offers":{"@type":"Offer","price":429000,"priceCurrency":"KRW","availability":"https://schema.org/InStock","url":"%s?item_code=%s"}}
                    """.formatted(color, color, code, CANONICAL, code));
            options.append("<option value='").append(color).append("'>").append(color).append("</option>");
        }
        return "<link rel='canonical' href='" + CANONICAL + "'><script type='application/ld+json'>"
                + "{\"@type\":\"ProductGroup\",\"name\":\"모멘텀 트루 와이어리스 5\",\"productGroupID\":\"cafe24_bnispace_1_731\",\"variesBy\":[\"color\"],\"hasVariant\":["
                + String.join(",", variants) + "]}</script>"
                + "<div class='xans-product-detail'><span class='senn-final-price'>₩429,000</span>"
                + "<select product_type='product_option' option_product_no='731' option_title='컬러'>" + options + "</select>"
                + "<div class='productAction'><a class='btnSubmit gFull' href='#none'><span id='actionBuy'>바로 구매하기</span></a></div></div>";
    }
    private static OfficialProductParser.Result inspect(String html) {
        return OfficialProductParser.inspect(URL, new WebEvidenceProvider.Page("", "", html, URL), Instant.now(), true);
    }
    @Test void requiresSameModelColorOptionsStockAndUnconditionalPrice() {
        var html = fixture();
        assertThat(inspect(html).product().offer().price()).isEqualByComparingTo("429000");
        assertThat(inspect(html.replace("href='#none'", "href='" + URL + "#none'")).product()).isNotNull();
        for (var bad : java.util.List.of(
                html.replaceFirst("429000", "399000"), html.replace("₩429,000", "₩399,000"),
                html.replaceFirst("InStock", "OutOfStock"), html.replaceFirst("P0000BCD000B", "P0000BCD000Z"),
                html.replace("option_product_no='731'", "option_product_no='732'"),
                html.replace("value='크림'", "value='크림' disabled"),
                html.replace("class='btnSubmit gFull'", "class='btnSubmit gFull displaynone'"),
                html.replace("class='btnSubmit gFull'", "class='btnSubmit gFull' aria-disabled='true'"),
                html.replace("\"priceCurrency\":\"KRW\"", "\"priceCurrency\":\"KRW\",\"eligibleCustomerType\":\"member\""),
                html.replace("href='#none'", "href='https://untrusted.example/'"),
                html.replace(" href='#none'", ""),
                html.replace("바로 구매하기", "품절"), html.replace("productGroupID\":\"cafe24_bnispace_1_731", "productGroupID\":\"different"),
                html.replace("kr.sennheiser-hearing.com", "kr.sennheiser-hearing.com.evil.example"))) {
            assertThat(inspect(bad).product()).isNull();
        }
        assertThat(OfficialProductParser.inspect(URL, new WebEvidenceProvider.Page("", "", html, URL), Instant.now()).product()).isNull();
        assertThat(OfficialStorePolicy.store(URL.replace("731", "732"))).isEmpty();
        assertThat(OfficialStorePolicy.store(URL + "&extra=1")).isEmpty();
    }
}
