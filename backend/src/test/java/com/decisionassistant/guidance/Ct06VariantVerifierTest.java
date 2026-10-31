package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class Ct06VariantVerifierTest {
    private static final String NAME = Ct06VariantVerifier.NAME;
    private static final String URL = "https://qcystore.co.kr/product/ct06/994/";
    private static final String[] COLORS = {"CT06 블랙", "CT06 화이트", "CT06 퍼플"};
    private static final String[] CODES = {"P0000BMG000F", "P0000BMG000G", "P0000BMG000J"};
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static String group() {
        var variants = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (int i = 0; i < COLORS.length; i++) variants.add(java.util.Map.of("@type", "Product", "name", NAME + " " + COLORS[i],
                "color", COLORS[i], "sku", "cafe24_ylintlshop_1_994_" + CODES[i],
                "offers", java.util.Map.of("url", URL + "?item_code=" + CODES[i], "availability", "https://schema.org/InStock")));
        return JSON.writeValueAsString(java.util.Map.of("@type", "ProductGroup", "name", NAME, "productGroupID", "cafe24_ylintlshop_1_994",
                "variesBy", java.util.List.of("color"), "hasVariant", variants));
    }
    private static String html() {
        var html = new StringBuilder("<div class='xans-product-detail'><select product_type='product_option' option_product_no='994' option_title='색상'><option value='*'>선택</option>");
        for (int i = 0; i < COLORS.length; i++) html.append("<option value='").append(CODES[i]).append("'>").append(COLORS[i]).append("</option>");
        return html.append("</select></div>").toString();
    }
    private static boolean matches(String html, String json) { return Ct06VariantVerifier.matches(Jsoup.parse(html), JSON.readTree(json)); }
    @Test void requiresExactInStockColorsAndCorrespondingPurchasingOptions() {
        var html = html(); var group = group();
        assertThat(matches(html, group)).isTrue();
        assertThat(matches(html, group.replace("InStock", "OutOfStock"))).isFalse();
        assertThat(matches(html, group.replace("P0000BMG000J", "P0000BMG000K"))).isFalse();
        assertThat(matches(html, group.replace("CT06 퍼플", "CT06 블루"))).isFalse();
        assertThat(matches(html.replace("value='P0000BMG000F'", "value='P0000BMG000F' disabled"), group)).isFalse();
        assertThat(matches(html.replace("CT06 퍼플", "CT06 블루"), group)).isFalse();
        assertThat(matches(html.replace("</select>", "<option value='other'>다른 구성</option></select>"), group)).isFalse();
        assertThat(matches(html.replace("option_product_no='994'", "option_product_no='995'"), group)).isFalse();
    }
}
