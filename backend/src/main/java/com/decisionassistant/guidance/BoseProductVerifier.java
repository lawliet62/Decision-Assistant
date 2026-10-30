package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.Optional;

/** 국내 공식몰 상품 307의 단일 색상과 구매 가격만 확인한다. */
final class BoseProductVerifier {
    static final String URL = "https://www.bose.co.kr/357/?idx=307";
    static final String NAME = "[New Launch] BOSE 보스 QC 이어버드(New)(아이스 블루)";
    private static final String PRODUCT_CODE = "s2026011323a13db67b522";
    private static final String OPTION_CODE = "O20260113696597912a514";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private BoseProductVerifier() {}

    static Optional<BigDecimal> price(String html) {
        if (html == null || html.length() > 2_000_000) return Optional.empty();
        var doc = Jsoup.parse(html, URL);
        var canonicals = doc.select("link[rel=canonical]");
        if (canonicals.size() != 1 || !URL.equals(canonicals.first().absUrl("href"))) return Optional.empty();
        var products = new java.util.ArrayList<tools.jackson.databind.JsonNode>();
        for (var script : doc.select("script[type=application/ld+json]")) {
            try {
                var item = JSON.readTree(script.data());
                if ("Product".equals(item.path("@type").asText(""))) products.add(item);
            } catch (RuntimeException ignored) { return Optional.empty(); }
        }
        if (products.size() != 1) return Optional.empty();
        var product = products.getFirst(); var offer = product.path("offers");
        if (!NAME.equals(product.path("name").asText("")) || !"BOSE".equals(product.path("brand").path("name").asText(""))
                || !"Offer".equals(offer.path("@type").asText("")) || !"KRW".equals(offer.path("priceCurrency").asText(""))
                || !"http://schema.org/InStock".equals(offer.path("availability").asText(""))
                || !"https://bose.co.kr/shop_view/?idx=307".equals(offer.path("url").asText(""))
                || offer.has("priceSpecification") || offer.has("eligibleCustomerType") || offer.has("eligibleQuantity")
                || offer.has("validForMemberTier") || offer.has("businessFunction") || offer.has("priceValidUntil")
                || offer.has("itemCondition") && !"https://schema.org/NewCondition".equals(offer.path("itemCondition").asText(""))) return Optional.empty();
        var raw = offer.path("price").asText("");
        if (!raw.matches("[1-9][0-9]{0,7}")) return Optional.empty();
        var price = new BigDecimal(raw);
        var formatted = String.format(java.util.Locale.US, "%,d", price.longValueExact()) + "원";
        var roots = doc.select("#prod_goods_form");
        if (roots.size() != 1) return Optional.empty();
        var root = roots.first();
        var names = root.select("h1.view_tit"); var displays = root.select(".pay_detail .real_price");
        var options = root.select("#prod_options label[data-opttype=color]");
        var inputs = root.select("#prod_options input, #prod_options select, #prod_options button");
        var buy = root.select(".buy_btns.pc a._btn_buy");
        if (root.select("#prod_options").size() != 1 || names.size() != 1 || !NAME.equals(names.first().ownText().trim()) || displays.size() != 1
                || !formatted.equals(displays.first().text()) || options.size() != 1 || inputs.size() != 1 || buy.size() != 1)
            return Optional.empty();
        var option = options.first(); var input = inputs.first(); var button = buy.first();
        if (!OPTION_CODE.equals(option.attr("data-optcode")) || !("아이스 블루 (" + formatted + ")").equals(option.attr("data-title"))
                || !"radio".equals(input.attr("type")) || !OPTION_CODE.equals(input.attr("value")) || input.parent() != option
                || input.hasAttr("disabled") || "true".equalsIgnoreCase(input.attr("aria-disabled"))
                || !PRODUCT_CODE.equals(button.attr("data-bs-prod-code")) || !"구매하기".equals(button.text())
                || !visible(input, root) || !visible(displays.first(), root) || !visible(button, root)) return Optional.empty();
        return Optional.of(price);
    }

    private static boolean visible(org.jsoup.nodes.Element element, org.jsoup.nodes.Element root) {
        for (var e = element; e != null && e != root; e = e.parent()) {
            var style = e.attr("style").replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
            if (e.hasAttr("disabled") || e.hasAttr("hidden") || e.hasClass("disabled") || e.hasClass("displaynone")
                    || "true".equalsIgnoreCase(e.attr("aria-disabled")) || "true".equalsIgnoreCase(e.attr("aria-hidden"))
                    || style.contains("display:none") || style.contains("visibility:hidden")) return false;
        }
        return true;
    }
}
