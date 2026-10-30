package com.decisionassistant.guidance;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
public class AnkerP41iVerifierTest {
    public static String page() {
        return "<title>" + AnkerP41iVerifier.TITLE + "</title><h1>" + AnkerP41iVerifier.NAME + "</h1>"
                + "<link rel='canonical' href='https://ankerkorea.co.kr/product/anker/91/'>"
                + "<script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"" + AnkerP41iVerifier.NAME
                + "\",\"sku\":\"cafe24_ankerkr_1_91\",\"offers\":{\"@type\":\"Offer\",\"url\":\"https://ankerkorea.co.kr/product/anker/91/\","
                + "\"price\":129990,\"priceCurrency\":\"KRW\",\"availability\":\"https://schema.org/InStock\",\"priceValidUntil\":\"2030-12-31 00:00\"}}</script>"
                + "<script>var product_code = 'P00000DN';var item_code = 'P00000DN000A';var product_sale_price = 69900;"
                + "var single_option_stock_data = '{\\\"use_stock\\\":true,\\\"use_soldout\\\":\\\"T\\\",\\\"stock_number\\\":71,\\\"is_reserve_stat\\\":\\\"N\\\"}';</script>"
                + "<div class='xans-product-detail'><table><tr><th>판매가</th><td><strong id='span_product_price_text'>129,990원</strong></td></tr>"
                + "<tr><th>할인가</th><td><span id='span_optimum_discount_price'>69,900원</span></td></tr></table>"
                + "<div class='productAction'><a><span id='actionBuy'>구매하기</span></a></div></div>";
    }
    public static String spec() {
        return "<title>Soundcore P41i | 完全ワイヤレスイヤホンの製品情報 | Anker Japan 公式オンラインストア</title>"
                + "<link rel='canonical' href='" + AnkerP41iVerifier.SPEC_URL + "'><h1>Soundcore P41i</h1>"
                + "<div class='pfs-text'><h3>ウルトラノイズキャンセリング 2.0</h3><p>" + AnkerP41iVerifier.QUOTE + "し、音楽に集中できます。</p></div>";
    }
    @Test void singleProductRequiresExactPriceStockAndIdentity() {
        var page = page(); var now = Instant.parse("2026-10-30T00:00:00Z");
        assertThat(AnkerP41iVerifier.price(page, now)).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("69900"));
        for (var invalid : new String[]{page.replace("InStock", "OutOfStock"),page.replace("P00000DN000A", "P00000DN000B"),
                page.replace("stock_number\\\":71", "stock_number\\\":0"),page.replace("할인가</th>", "회원 할인가</th>"),
                page.replace("69,900원", "59,900원"),page.replace("A3937", "A3936"),page.replace("2030-12-31", "2020-12-31"),
                page.replace("/anker/91/\",", "/anker/91/?coupon=1\","),page.replace("\"KRW\"", "\"USD\""),
                page.replace("구매하기", "품절"),page.replace("<a><span", "<a style='display:none'><span")})
            assertThat(AnkerP41iVerifier.price(invalid, now)).isEmpty();
    }
    @Test void manufacturerSpecRequiresModelAndAdaptiveFeature() {
        var spec = spec(); assertThat(AnkerP41iVerifier.specification(spec)).isTrue();
        assertThat(AnkerP41iVerifier.specification(spec.replace("a3937", "a3936"))).isFalse();
        assertThat(AnkerP41iVerifier.specification(spec.replace("P41i", "P40i"))).isFalse();
        assertThat(AnkerP41iVerifier.specification(spec.replace("自動で調節し、", "調節しません。"))).isFalse();
    }
}
