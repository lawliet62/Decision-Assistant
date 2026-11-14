package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

/** 국내 공식몰 상품 731의 동일 가격 색상 구성만 확인한다. */
final class SennheiserProductVerifier {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NAME = "모멘텀 트루 와이어리스 5";
    private static final Map<String, String> COLORS = Map.of(
            "블랙 그래파이트", "P0000BCD000B", "크림", "P0000BCD000C", "데님", "P0000BCD000D",
            "라벤더", "P0000BCD000E", "코퍼", "P0000BCD000F");
    private SennheiserProductVerifier() {}

    static Optional<BigDecimal> price(String html) {
        if (html == null || html.length() > 2_000_000) return Optional.empty();
        var doc = Jsoup.parse(html);
        var canonical = doc.select("link[rel=canonical]");
        if (canonical.size() != 1) return Optional.empty();
        var canonicalUrl = canonical.first().attr("href");
        var name = nameForCanonical(canonicalUrl).orElse(null);
        if (name == null) return Optional.empty();
        var groups = new ArrayList<tools.jackson.databind.JsonNode>();
        for (var script : doc.select("script[type=application/ld+json]")) {
            try {
                var node = JSON.readTree(script.data());
                if ("ProductGroup".equals(node.path("@type").asText(""))) groups.add(node);
            } catch (RuntimeException e) { return Optional.empty(); }
        }
        if (groups.size() != 1) return Optional.empty();
        var group = groups.getFirst();
        if (!name.equals(group.path("name").asText("")) || !"cafe24_bnispace_1_731".equals(group.path("productGroupID").asText(""))
                || !group.path("variesBy").isArray() || group.path("variesBy").size() != 1
                || !"color".equals(group.path("variesBy").get(0).asText("")) || !group.path("hasVariant").isArray()
                || group.path("hasVariant").size() != COLORS.size()) return Optional.empty();
        var seen = new HashSet<String>();
        var prices = new HashSet<BigDecimal>();
        for (var variant : group.path("hasVariant")) {
            var color = variant.path("color").asText("");
            var code = COLORS.get(color);
            var offer = variant.path("offers");
            if (code == null || !seen.add(color) || !"Product".equals(variant.path("@type").asText(""))
                    || !(name + " " + color).equals(variant.path("name").asText(""))
                    || !("cafe24_bnispace_1_731_" + code).equals(variant.path("sku").asText(""))
                    || !"Offer".equals(offer.path("@type").asText("")) || !"KRW".equals(offer.path("priceCurrency").asText(""))
                    || !"https://schema.org/InStock".equals(offer.path("availability").asText(""))
                    || offer.has("priceSpecification") || offer.has("eligibleCustomerType") || offer.has("eligibleQuantity")
                    || offer.has("validForMemberTier") || offer.has("businessFunction") || offer.has("priceValidUntil")
                    || offer.has("itemCondition") && !"https://schema.org/NewCondition".equals(offer.path("itemCondition").asText(""))
                    || !offerUrl(offer.path("url").asText(""), code, canonicalUrl)) return Optional.empty();
            var raw = offer.path("price").asText("");
            if (!raw.matches("[1-9][0-9]{0,7}")) return Optional.empty();
            prices.add(new BigDecimal(raw));
        }
        if (prices.size() != 1) return Optional.empty();
        var price = prices.iterator().next();
        var roots = doc.select(".xans-product-detail");
        if (roots.size() != 1) return Optional.empty();
        var root = roots.first();
        var select = root.select("select[option_product_no=731][option_title=컬러]");
        if (select.size() != 1 || root.select("select[product_type=product_option]").size() != 1) return Optional.empty();
        var options = select.first().select("option:not([disabled])").stream().map(e -> e.attr("value"))
                .filter(v -> !v.equals("*") && !v.equals("**")).toList();
        if (options.size() != COLORS.size() || !new HashSet<>(options).equals(COLORS.keySet())) return Optional.empty();
        var display = root.select(".senn-final-price");
        if (display.size() != 1 || !display.first().text().matches("₩[0-9,]+")) return Optional.empty();
        try {
            if (price.compareTo(new BigDecimal(display.first().text().substring(1).replace(",", ""))) != 0) return Optional.empty();
        } catch (NumberFormatException e) { return Optional.empty(); }
        var buy = root.select(".productAction #actionBuy");
        if (buy.size() != 1 || !"바로 구매하기".equals(buy.first().text())) return Optional.empty();
        var button = buy.first().closest("a.btnSubmit.gFull");
        if (button == null || !Set.of("#none", "https://kr.sennheiser-hearing.com/product/detail.html?product_no=731#none")
                .contains(button.attr("href"))) return Optional.empty();
        for (var element = buy.first(); element != null && element != root; element = element.parent())
            if (element.hasClass("displaynone") || element.hasClass("disabled") || element.hasAttr("disabled")
                    || "true".equalsIgnoreCase(element.attr("aria-disabled")) || "true".equalsIgnoreCase(element.attr("aria-hidden"))
                    || element.attr("style").replaceAll("\\s+", "").toLowerCase(Locale.ROOT).matches(".*(?:display:none|visibility:hidden).*")) return Optional.empty();
        return Optional.of(price);
    }

    // 확인한 행사명 두 형태만 허용한다. 임의 접두사 제거·상품 번호만으로의 승격은 하지 않는다.
    static Optional<String> nameForCanonical(String url) {
        try {
            var uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !"kr.sennheiser-hearing.com".equals(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null
                    || uri.getFragment() != null || OfficialStorePolicy.store(url).isEmpty()) return Optional.empty();
            return switch (uri.getPath()) {
                case "/product/모멘텀-트루-와이어리스-5/731/" -> Optional.of(NAME);
                case "/product/젠사페-모멘텀-트루-와이어리스-5/731/" -> Optional.of("[젠사페] " + NAME);
                default -> Optional.empty();
            };
        } catch (RuntimeException e) { return Optional.empty(); }
    }
    private static boolean offerUrl(String url, String code, String canonicalUrl) {
        try {
            var uri = URI.create(url);
            return "https".equals(uri.getScheme()) && "kr.sennheiser-hearing.com".equals(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1
                    && uri.getFragment() == null && OfficialStorePolicy.store(url).isPresent()
                    && URI.create(canonicalUrl).getPath().equals(uri.getPath())
                    && ("item_code=" + code).equals(uri.getRawQuery());
        } catch (RuntimeException e) { return false; }
    }
}
