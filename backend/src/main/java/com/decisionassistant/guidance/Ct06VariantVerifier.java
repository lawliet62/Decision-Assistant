package com.decisionassistant.guidance;

import org.jsoup.nodes.Document;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;

/** 공식몰 CT06 상품의 모든 색상과 구매 선택지를 연결한다. */
final class Ct06VariantVerifier {
    static final String URL = "https://qcystore.co.kr/product/detail.html?product_no=994";
    static final String NAME = "QCY CT06 오픈형 귀찌 무선 블루투스 이어폰 러닝 자전거 운동용 이어커프";
    private static final Map<String, String> COLORS = Map.of("CT06 블랙", "P0000BMG000F", "CT06 화이트", "P0000BMG000G", "CT06 퍼플", "P0000BMG000J");
    private Ct06VariantVerifier() {}
    static boolean matches(Document doc, JsonNode group) {
        if (!"ProductGroup".equals(group.path("@type").asText("")) || !NAME.equals(group.path("name").asText(""))
                || !"cafe24_ylintlshop_1_994".equals(group.path("productGroupID").asText(""))
                || !group.path("variesBy").isArray() || group.path("variesBy").size() != 1
                || !"color".equals(group.path("variesBy").get(0).asText("")) || !group.path("hasVariant").isArray()
                || group.path("hasVariant").size() != COLORS.size()) return false;
        var seen = new HashSet<String>();
        for (var variant : group.path("hasVariant")) {
            var color = variant.path("color").asText(""); var code = COLORS.get(color); var offer = variant.path("offers");
            if (code == null || !seen.add(color) || !"Product".equals(variant.path("@type").asText(""))
                    || !(NAME + " " + color).equals(variant.path("name").asText(""))
                    || !("cafe24_ylintlshop_1_994_" + code).equals(variant.path("sku").asText(""))
                    || !"https://schema.org/InStock".equals(offer.path("availability").asText(""))) return false;
            try {
                var uri = URI.create(offer.path("url").asText(""));
                if (!OfficialProductParser.samePage(URL, uri.toString()) || !("item_code=" + code).equals(uri.getRawQuery()) || uri.getFragment() != null) return false;
            } catch (RuntimeException ignored) { return false; }
        }
        var selects = doc.select(".xans-product-detail select[product_type=product_option]");
        if (selects.size() != 1 || !"994".equals(selects.first().attr("option_product_no"))
                || !"색상".equals(selects.first().attr("option_title")) || selects.first().hasAttr("disabled")) return false;
        var selected = new HashSet<String>();
        for (var option : selects.first().select("option")) {
            var code = option.attr("value");
            if ("*".equals(code) || "**".equals(code)) continue;
            var name = option.text();
            if (option.hasAttr("disabled") || !code.equals(COLORS.get(name)) || !selected.add(name)) return false;
        }
        return selected.equals(COLORS.keySet());
    }
}
