package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;

/** 고정된 국내 공식 상품의 모델·색상·구매 영역을 함께 확인한다. */
final class AudioTechnicaProductVerifier {
    static final String URL = "https://audio-technica.co.kr/product/ath-sq1tw2nc/955/";
    static final String NAME = "ATH-SQ1TW2NC";
    static final String AC5_URL = "https://audio-technica.co.kr/product/ath-ac5tw/921/";
    static final String AC5_NAME = "ATH-AC5TW";
    private static final String GROUP = "cafe24_audiotechnica_1_955";
    private static final String ALIAS = "https://www.audio-technica.kr/product/ath-sq1tw2nc/955/";
    private static final Map<String, String> COLORS = Map.of("블랙", "P0000BKT000A", "그레이", "P0000BKT000B", "포레스트그린", "P0000BKT000C", "카페라테", "P0000BKT000D");
    private static final Map<String, String> AC5_COLORS = Map.of("블랙", "P0000BJL000A", "리치 화이트", "P0000BJL000B");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private AudioTechnicaProductVerifier() {}

    static Optional<BigDecimal> price(String html) {
        return price(html, URL, NAME, GROUP, ALIAS, COLORS);
    }
    static Optional<BigDecimal> ac5Price(String html) {
        return price(html, AC5_URL, AC5_NAME, "cafe24_audiotechnica_1_921", "https://www.audio-technica.kr/product/ath-ac5tw/921/", AC5_COLORS);
    }
    private static Optional<BigDecimal> price(String html, String url, String name, String groupId, String alias, Map<String, String> colors) {
        if (html == null || html.length() > 2_000_000) return Optional.empty();
        var doc = Jsoup.parse(html, url);
        var canonical = doc.select("link[rel=canonical]");
        if (canonical.size() != 1 || !url.equals(canonical.first().absUrl("href"))) return Optional.empty();
        var groups = new ArrayList<JsonNode>();
        for (var script : doc.select("script[type=application/ld+json]")) {
            try { var node = JSON.readTree(script.data()); if ("ProductGroup".equals(node.path("@type").asText(""))) groups.add(node); }
            catch (RuntimeException e) { return Optional.empty(); }
        }
        if (groups.size() != 1) return Optional.empty();
        var group = groups.getFirst();
        var groupUrl = group.path("@id").asText("");
        if (!name.equals(group.path("name").asText("")) || !groupId.equals(group.path("productGroupID").asText(""))
                || !Set.of(url, alias).contains(groupUrl) || !group.path("variesBy").isArray()
                || group.path("variesBy").size() != 1 || !"color".equals(group.path("variesBy").get(0).asText(""))
                || !group.path("hasVariant").isArray() || group.path("hasVariant").size() != colors.size()) return Optional.empty();
        var seen = new HashSet<String>(); var prices = new HashSet<BigDecimal>();
        for (var variant : group.path("hasVariant")) {
            var color = variant.path("color").asText(""); var code = colors.get(color); var offer = variant.path("offers");
            if (code == null || !seen.add(color) || !"Product".equals(variant.path("@type").asText(""))
                    || !(name + " " + color).equals(variant.path("name").asText(""))
                    || !(groupId + "_" + code).equals(variant.path("sku").asText(""))
                    || !"Offer".equals(offer.path("@type").asText("")) || !"KRW".equals(offer.path("priceCurrency").asText(""))
                    || !"https://schema.org/InStock".equals(offer.path("availability").asText(""))
                    || !(groupUrl + "?item_code=" + code).equals(offer.path("url").asText(""))
                    || offer.has("priceSpecification") || offer.has("eligibleCustomerType") || offer.has("eligibleQuantity")
                    || offer.has("validForMemberTier") || offer.has("businessFunction") || offer.has("priceValidUntil")
                    || offer.has("itemCondition") && !"https://schema.org/NewCondition".equals(offer.path("itemCondition").asText(""))) return Optional.empty();
            var raw = offer.path("price").asText(""); if (!raw.matches("[1-9][0-9]{0,7}")) return Optional.empty();
            prices.add(new BigDecimal(raw));
        }
        if (prices.size() != 1) return Optional.empty();
        var root = doc.select(".xans-product-detail"); if (root.size() != 1) return Optional.empty();
        var headings = root.first().select(".detail_info h2, .detail_name h2");
        if (headings.size() != 1 || !name.equals(headings.first().text())) return Optional.empty();
        var selects = root.first().select("select[product_type=product_option]");
        if (selects.size() != 1 || !"색상".equals(selects.first().attr("option_title"))) return Optional.empty();
        var options = selects.first().select("option:not([disabled])").stream().map(e -> e.attr("value"))
                .filter(v -> !v.equals("*") && !v.equals("**")).toList();
        if (options.size() != colors.size() || !new HashSet<>(options).equals(colors.keySet())) return Optional.empty();
        var display = root.first().select(".detail_price .product_price");
        if (display.size() != 1 || !display.first().text().matches("[0-9,]+원")) return Optional.empty();
        var price = prices.iterator().next();
        try { if (price.compareTo(new BigDecimal(display.first().text().replace("원", "").replace(",", ""))) != 0) return Optional.empty(); }
        catch (NumberFormatException e) { return Optional.empty(); }
        if (root.first().select(".product_sale_price").stream().anyMatch(e -> !e.text().isBlank())) return Optional.empty();
        var buys = root.first().select("#actionBuy").stream().filter(e -> visible(e, root.first())).toList();
        if (buys.isEmpty() || buys.size() > 2) return Optional.empty();
        if (buys.size() == 2 && (buys.stream().filter(e -> e.closest(".display_pc_only") != null).count() != 1
                || buys.stream().filter(e -> e.closest(".gFixed") != null).count() != 1)) return Optional.empty();
        for (var buy : buys) {
            var anchor = buy.closest("a.btnSubmit");
            if (!"바로구매".equals(buy.text()) || anchor == null || !Set.of("#none", url + "#none").contains(anchor.attr("href"))) return Optional.empty();
        }
        return Optional.of(price);
    }
    private static boolean visible(Element e, Element root) {
        for (var node = e; node != null && node != root; node = node.parent())
            if (node.hasClass("displaynone") || node.hasClass("disabled") || node.hasAttr("disabled")
                    || "true".equalsIgnoreCase(node.attr("aria-hidden")) || "true".equalsIgnoreCase(node.attr("aria-disabled"))
                    || node.attr("style").replaceAll("\\s+", "").toLowerCase(Locale.ROOT).matches(".*(?:display:none|visibility:hidden).*")) return false;
        return true;
    }
}
