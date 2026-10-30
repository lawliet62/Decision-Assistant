package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class AnkerP31iVerifierTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String[] COLORS = {"블랙", "블루", "핑크", "화이트"};
    private static final String[] CODES = {"P00000BR000B", "P00000BR000C", "P00000BR000D", "P00000BR000E"};
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static String page() {
        var variants = new ArrayList<Map<String, Object>>(); var stock = new LinkedHashMap<String, Object>();
        var options = new StringBuilder("<option value='*'>선택</option><option value='**' disabled>---</option>");
        for (int i = 0; i < COLORS.length; i++) {
            var color = COLORS[i]; var code = CODES[i];
            variants.add(Map.of("@type", "Product", "name", AnkerP31iProductVerifier.NAME + " " + color, "sku", "cafe24_ankerkr_1_43_" + code,
                    "additionalProperty", List.of(Map.of("@type", "PropertyValue", "name", "색상선택", "value", color)),
                    "offers", Map.of("@type", "Offer", "url", "https://ankerkorea.co.kr/product/anker/43/?item_code=" + code,
                            "price", 59990, "priceCurrency", "KRW", "availability", "https://schema.org/InStock", "priceValidUntil", "2030-12-31 00:00")));
            stock.put(code, Map.of("option_value", color, "is_selling", "T", "is_display", "T", "is_auto_soldout", "F", "is_reserve_stat", "N",
                    "option_price", 59990, "stock_price", "0.00", "origin_option_added_price", "0.00"));
            options.append("<option value='").append(color).append("'>").append(color).append("</option>");
        }
        var group = JSON.writeValueAsString(Map.of("@type", "ProductGroup", "name", AnkerP31iProductVerifier.NAME,
                "productGroupID", "cafe24_ankerkr_1_43", "variesBy", List.of("색상선택"), "hasVariant", variants));
        var stockString = JSON.writeValueAsString(stock).replace("\\", "\\\\").replace("\"", "\\\"");
        return "<link rel='canonical' href='https://ankerkorea.co.kr/product/anker/43/'>"
                + "<script type='application/ld+json'>" + group + "</script><script>var option_stock_data = '" + stockString + "';var product_sale_price = 39900;</script>"
                + "<div class='xans-product-detail'><table><tr><th>판매가</th><td><strong id='span_product_price_text'>59,990원</strong></td></tr>"
                + "<tr><th>할인가</th><td><span id='span_optimum_discount_price'>39,900원</span></td></tr></table>"
                + "<select product_type='product_option' option_product_no='43' option_title='색상선택'>" + options + "</select>"
                + "<div class='productAction'><a><span id='actionBuy'>구매하기</span></a></div></div>";
    }
    @Test void discountedPriceRequiresMatchingModelStockOptionsAndUnconditionalDisplay() {
        var html = page();
        assertThat(AnkerP31iProductVerifier.price(html, NOW)).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("39900"));
        for (var invalid : List.of(html.replace("InStock", "OutOfStock"), html.replace("P00000BR000B", "P00000BR000Z"),
                html.replace("색상선택", "사은품"), html.replace("39,900원", "34,900원"), html.replace("product_sale_price = 39900", "product_sale_price = 34000"),
                html.replace("할인가</th>", "회원 할인가</th>"), html.replace("39,900원</span>", "39,900원<span>회원 전용</span></span>"), html.replace("구매하기", "품절"),
                html.replace("value='블랙'", "value='블랙' disabled"), html.replace("<a><span id='actionBuy'", "<a style='display:none'><span id='actionBuy'"),
                html.replace("2030-12-31 00:00", "2020-01-01 00:00"), html.replace("0.00", "1000.00")))
            assertThat(AnkerP31iProductVerifier.price(invalid, NOW)).isEmpty();
        assertThat(OfficialStorePolicy.store(AnkerP31iProductVerifier.URL.replace("43", "44"))).isEmpty();
    }
    @Test void manufacturerSpecRequiresModelAndAffirmativeAncText() {
        var url = AnkerP31iSpecificationVerifier.URL;
        var page = "<title>Soundcore P31i | 完全ワイヤレスイヤホンの製品情報 | Anker Japan 公式オンラインストア</title>"
                + "<link rel='canonical' href='" + url + "'><h1>Soundcore P31i</h1>"
                + "<meta name='description' content='Soundcore P31iならAnker (アンカー) Japan 公式オンラインストア。アクティブノイズキャンセリング搭載。'>";
        assertThat(AnkerP31iSpecificationVerifier.matches(page)).isTrue();
        assertThat(AnkerP31iSpecificationVerifier.matches(page.replace("P31i", "P30i"))).isFalse();
        assertThat(AnkerP31iSpecificationVerifier.matches(page.replace("d1202", "d1201"))).isFalse();
        assertThat(AnkerP31iSpecificationVerifier.matches(page.replace("搭載", "非搭載"))).isFalse();
        assertThat(AnkerP31iSpecificationVerifier.matches(page + "<h1>他の製品</h1>")).isFalse();
    }
}
