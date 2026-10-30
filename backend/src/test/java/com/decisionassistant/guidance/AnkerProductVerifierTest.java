package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class AnkerProductVerifierTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String[] COLORS = {"블랙", "블루", "핑크", "화이트"};
    private static final String[] CODES = {"P00000BR000B", "P00000BR000C", "P00000BR000D", "P00000BR000E"};
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static String page() { return page(0); }
    private static String page(boolean aero) { return page(aero ? 1 : 0); }
    private static String page(int type) {
        var variants = new ArrayList<Map<String, Object>>(); var stock = new LinkedHashMap<String, Object>();
        var options = new StringBuilder("<option value='*'>선택</option><option value='**' disabled>---</option>");
        var colors = type == 1 ? new String[]{"블랙", "화이트", "핑크", "블루"} : type == 2 ? new String[]{"블랙", "화이트"} : COLORS;
        var codes = type == 1 ? new String[]{"P00000BT000B", "P00000BT000C", "P00000BT000D", "P00000BT000E"}
                : type == 2 ? new String[]{"P00000BO000B", "P00000BO000C"} : CODES;
        var number = type == 1 ? "45" : type == 2 ? "40" : "43";
        var name = type == 1 ? AnkerProductVerifier.AERO_NAME : type == 2 ? AnkerProductVerifier.LIBERTY_NAME : AnkerProductVerifier.NAME;
        var dimension = type == 2 ? "옵션선택" : "색상선택";
        for (int i = 0; i < colors.length; i++) {
            var color = colors[i]; var code = codes[i];
            variants.add(Map.of("@type", "Product", "name", name + " " + color, "sku", "cafe24_ankerkr_1_" + number + "_" + code,
                    "additionalProperty", List.of(Map.of("@type", "PropertyValue", "name", dimension, "value", color)),
                    "offers", Map.of("@type", "Offer", "url", "https://ankerkorea.co.kr/product/anker/" + number + "/?item_code=" + code,
                            "price", 59990, "priceCurrency", "KRW", "availability", "https://schema.org/InStock", "priceValidUntil", "2030-12-31 00:00")));
            stock.put(code, Map.of("option_value", color, "is_selling", "T", "is_display", "T", "is_auto_soldout", "F", "is_reserve_stat", "N",
                    "option_price", 59990, "stock_price", "0.00", "origin_option_added_price", "0.00"));
            options.append("<option value='").append(color).append("'>").append(color).append("</option>");
        }
        var group = JSON.writeValueAsString(Map.of("@type", "ProductGroup", "name", name,
                "productGroupID", "cafe24_ankerkr_1_" + number, "variesBy", List.of(dimension), "hasVariant", variants));
        var stockString = JSON.writeValueAsString(stock).replace("\\", "\\\\").replace("\"", "\\\"");
        return "<link rel='canonical' href='https://ankerkorea.co.kr/product/anker/" + number + "/'>"
                + "<script type='application/ld+json'>" + group + "</script><script>var option_stock_data = '" + stockString + "';var product_sale_price = 39900;</script>"
                + "<div class='xans-product-detail'><table><tr><th>판매가</th><td><strong id='span_product_price_text'>59,990원</strong></td></tr>"
                + "<tr><th>할인가</th><td><span id='span_optimum_discount_price'>39,900원</span></td></tr></table>"
                + "<select product_type='product_option' option_product_no='" + number + "' option_title='" + dimension + "'>" + options + "</select>"
                + "<div class='productAction'><a><span id='actionBuy'>구매하기</span></a></div></div>";
    }
    @Test void libertyRequiresItsOwnModelAndTwoOptions() {
        var html = page(2);
        assertThat(AnkerProductVerifier.libertyPrice(html, NOW)).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("39900"));
        assertThat(AnkerProductVerifier.aeroclipPrice(html, NOW)).isEmpty();
        assertThat(AnkerProductVerifier.libertyPrice(html.replace("P00000BO000C", "P00000BO000D"), NOW)).isEmpty();
        assertThat(AnkerProductVerifier.libertyPrice(html.replace("옵션선택", "사은품"), NOW)).isEmpty();
        assertThat(AnkerProductVerifier.libertyPrice(html.replace("InStock", "OutOfStock"), NOW)).isEmpty();
        assertThat(OfficialStorePolicy.store(AnkerProductVerifier.LIBERTY_URL)).isPresent();
        assertThat(OfficialStorePolicy.store("https://ankerkorea.co.kr/product/anker/40/?item_code=P00000BT000B")).isEmpty();
    }
    @Test void libertyAncRequiresExactManufacturerModelAndPositiveFeature() {
        var url = AnkerLibertySpecificationVerifier.URL;
        var html = "<title>soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby 3D Audio</title>"
                + "<link rel='canonical' href='" + url + "'><h1>soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby Audio</h1>"
                + "<meta name='description' content='Buy soundcore Liberty 5 earbuds with Dolby 3D Audio, adaptive ANC, and premium sound.'>"
                + "<strong>Real-Time Adaptive ANC 3.0:</strong>";
        assertThat(AnkerLibertySpecificationVerifier.matches(html)).isTrue();
        assertThat(AnkerLibertySpecificationVerifier.matches(html.replace("a3957", "a3954"))).isFalse();
        assertThat(AnkerLibertySpecificationVerifier.matches(html.replace("Liberty 5", "Liberty 4"))).isFalse();
        assertThat(AnkerLibertySpecificationVerifier.matches(html.replace("adaptive ANC", "without ANC"))).isFalse();
        assertThat(AnkerLibertySpecificationVerifier.matches(html.replace("Real-Time Adaptive ANC 3.0", "No ANC"))).isFalse();
    }
    @Test void aeroclipUsesOnlyItsOwnModelAndColorCodes() {
        var html = page(true);
        assertThat(AnkerProductVerifier.aeroclipPrice(html, NOW)).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("39900"));
        assertThat(AnkerProductVerifier.price(html, NOW)).isEmpty();
        assertThat(AnkerProductVerifier.aeroclipPrice(html.replace("P00000BT000C", "P00000BT000X"), NOW)).isEmpty();
        assertThat(AnkerProductVerifier.aeroclipPrice(html.replace("InStock", "OutOfStock"), NOW)).isEmpty();
        assertThat(OfficialStorePolicy.store(AnkerProductVerifier.AERO_URL)).isPresent();
        assertThat(OfficialStorePolicy.store("https://ankerkorea.co.kr/product/anker/45/?item_code=P00000BR000B")).isEmpty();
    }
    @Test void discountedPriceRequiresMatchingModelStockOptionsAndUnconditionalDisplay() {
        var html = page();
        assertThat(AnkerProductVerifier.price(html, NOW)).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("39900"));
        for (var invalid : List.of(html.replace("InStock", "OutOfStock"), html.replace("P00000BR000B", "P00000BR000Z"),
                html.replace("색상선택", "사은품"), html.replace("39,900원", "34,900원"), html.replace("product_sale_price = 39900", "product_sale_price = 34000"),
                html.replace("할인가</th>", "회원 할인가</th>"), html.replace("39,900원</span>", "39,900원<span>회원 전용</span></span>"), html.replace("구매하기", "품절"),
                html.replace("value='블랙'", "value='블랙' disabled"), html.replace("<a><span id='actionBuy'", "<a style='display:none'><span id='actionBuy'"),
                html.replace("2030-12-31 00:00", "2020-01-01 00:00"), html.replace("2030-12-31 00:00", "2026-10-30 23:00"), html.replace("0.00", "1000.00")))
            assertThat(AnkerProductVerifier.price(invalid, NOW)).isEmpty();
        assertThat(OfficialStorePolicy.store(AnkerProductVerifier.URL.replace("43", "44"))).isEmpty();
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
    @Test void aeroclipSpecRequiresExactModelAndOpenForm() {
        var name = AnkerProductVerifier.AERO_NAME;
        var html = "<title>에어로클립 오픈형 러닝 블루투스이어폰 A3388</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://ankerkorea.co.kr/product/anker/45/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ankerkr_1_45\"}</script>";
        assertThat(AnkerAeroclipSpecificationVerifier.matches(html)).isTrue();
        assertThat(AnkerAeroclipSpecificationVerifier.matches(html.replace("오픈형", "커널형"))).isFalse();
        assertThat(AnkerAeroclipSpecificationVerifier.matches(html.replace("A3388", "A3389"))).isFalse();
        assertThat(AnkerAeroclipSpecificationVerifier.matches(html.replace("ankerkr_1_45", "ankerkr_1_43"))).isFalse();
        assertThat(AnkerAeroclipSpecificationVerifier.matches(html + "<h1>다른 모델</h1>")).isFalse();
    }
}
