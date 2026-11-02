package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class JabraEvolve2PriceVerifierTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static final String HTML = """
            <link rel='canonical' href='https://jabramall.com/product/핫딜-jabra-evolve2-buds/2162/'>
            <script type='application/ld+json'>
              {"@type":"ProductGroup","@id":"https://www.jabramall.com/product/핫딜-jabra-evolve2-buds/2162/",
               "name":"<b>[핫딜]</b> Jabra <b>Evolve2 Buds</b>","productGroupID":"cafe24_dnyint_6_2162","variesBy":["USB타입","플랫폼","Qi 무선충전패드"],
               "hasVariant":[{"@type":"Product","name":"<b>[핫딜]</b> Jabra <b>Evolve2 Buds</b> USB-A타입-UC-미포함",
                "sku":"cafe24_dnyint_6_2162_P0000DFE000H","additionalProperty":[
                 {"@type":"PropertyValue","name":"USB타입","value":"USB-A타입"},
                 {"@type":"PropertyValue","name":"플랫폼","value":"UC"},
                 {"@type":"PropertyValue","name":"Qi 무선충전패드","value":"미포함"}],"offers":{"@type":"Offer","priceCurrency":"KRW",
                "availability":"https://schema.org/InStock","url":"https://www.jabramall.com/product/핫딜-jabra-evolve2-buds/2162/?item_code=P0000DFE000H",
                "price":399000,"priceValidUntil":"2029-04-30 00:00"}}]}
            </script>
            <div class='xans-product-detail'><table>
              <tr><td><span id='span_product_price_text'>￦399,000</span></td></tr>
              <tr rel='할인가'><td><span id='span_product_price_sale'>￦359,100</span></td></tr>
              <tr rel='할인 기간'><td><span class='period'>2023-11-27 00:00 ~ 2029-04-30 00:00</span></td></tr>
            </table>
              <select product_type='product_option' option_product_no='2162' option_title='USB타입'><option value='USB-A타입'>USB-A타입</option></select>
              <select product_type='product_option' option_product_no='2162' option_title='플랫폼'><option value='UC'>UC</option></select>
              <select product_type='product_option' option_product_no='2162' option_title='Qi 무선충전패드'><option value='미포함'>미포함</option></select>
              <a class='btnSubmit' href='#none'><span id='actionBuy'>바로구매</span></a>
            </div>
            <script>
              var product_sale_price = 359100;
              var option_stock_data = '{"P0000DFE000H":{"option_value":"USB-A타입-UC-미포함","is_selling":"T","is_display":"T","is_auto_soldout":"F","is_reserve_stat":"N","use_stock":true,"option_price":399000,"stock_price":"0.00","origin_option_added_price":"0.00"}}';
            </script>
            """;

    @Test void matchesOnlyTheIdentifiedPurchasableConfigurationAndUnconditionalSale() {
        assertThat(JabraEvolve2PriceVerifier.price(HTML, NOW)).contains(new BigDecimal("359100"));
        assertThat(JabraEvolve2PriceVerifier.price(HTML.replace("P0000DFE000H", "P0000DFE000N"), NOW)).isEmpty();
        assertThat(JabraEvolve2PriceVerifier.price(HTML.replace("InStock", "OutOfStock"), NOW)).isEmpty();
        assertThat(JabraEvolve2PriceVerifier.price(HTML.replace("\"is_selling\":\"T\"", "\"is_selling\":\"F\""), NOW)).isEmpty();
        assertThat(JabraEvolve2PriceVerifier.price(HTML.replace("￦359,100", "￦350,000"), NOW)).isEmpty();
        assertThat(JabraEvolve2PriceVerifier.price(HTML.replace("\"value\":\"UC\"", "\"value\":\"MS\""), NOW)).isEmpty();
        assertThat(JabraEvolve2PriceVerifier.price(HTML.replace("\"stock_price\":\"0.00\"", "\"stock_price\":\"1000.00\""), NOW)).isEmpty();
        assertThat(JabraEvolve2PriceVerifier.price(HTML.replace("<tr rel='할인가'>", "<tr rel='할인가'><td>회원 전용</td>"), NOW)).isEmpty();
        assertThat(JabraEvolve2PriceVerifier.price(HTML.replace("2029-04-30 00:00</span>", "2026-11-04 00:00</span>"), NOW)).isEmpty();
        assertThat(JabraEvolve2PriceVerifier.price(HTML.replace("<span id='actionBuy'>", "<span id='actionBuy' hidden>"), NOW)).isEmpty();
        assertThat(JabraEvolve2PriceVerifier.price(HTML.replace("jabramall.com/product/핫딜", "other.example/product/핫딜"), NOW)).isEmpty();
    }
}
