package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/** 자브라 상품 2162의 USB-A·UC·충전 패드 미포함 구성만 판단한다. */
final class JabraEvolve2PriceVerifier {
    static final String URL = "https://www.jabramall.com/product/detail.html?product_no=2162";
    static final String SLUG = "/product/핫딜-jabra-evolve2-buds/2162/";
    static final String CODE = "P0000DFE000H";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern SALE = Pattern.compile("\\bvar\\s+product_sale_price\\s*=\\s*([0-9]+)\\s*;");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
    private JabraEvolve2PriceVerifier() {}

    static Optional<BigDecimal> price(String html, Instant now) {
        if (html == null || now == null || html.length() > 2_000_000) return Optional.empty();
        var doc = Jsoup.parse(html, URL);
        var canonical = doc.select("link[rel=canonical]");
        if (canonical.size() != 1 || !productUrl(canonical.first().absUrl("href"), false)) return Optional.empty();
        var groups = new ArrayList<JsonNode>();
        for (var script : doc.select("script[type=application/ld+json]")) {
            try { var value = JSON.readTree(script.data()); if ("ProductGroup".equals(value.path("@type").asText(""))) groups.add(value); }
            catch (RuntimeException e) { return Optional.empty(); }
        }
        if (groups.size() != 1) return Optional.empty();
        var group = groups.getFirst();
        if (!"[핫딜] Jabra Evolve2 Buds".equals(Jsoup.parse(group.path("name").asText("")).text())
                || !"cafe24_dnyint_6_2162".equals(group.path("productGroupID").asText(""))
                || !productUrl(group.path("@id").asText(""), false) || !group.path("variesBy").isArray() || group.path("variesBy").size() != 3
                || !"USB타입".equals(group.path("variesBy").get(0).asText("")) || !"플랫폼".equals(group.path("variesBy").get(1).asText(""))
                || !"Qi 무선충전패드".equals(group.path("variesBy").get(2).asText("")) || !group.path("hasVariant").isArray()
                || group.path("hasVariant").size() != 1) return Optional.empty();
        var variant = group.path("hasVariant").get(0); var offer = variant.path("offers");
        var properties = variant.path("additionalProperty");
        if (!properties.isArray() || properties.size() != 3) return Optional.empty();
        var expected = Map.of("USB타입", "USB-A타입", "플랫폼", "UC", "Qi 무선충전패드", "미포함");
        var seen = new HashSet<String>();
        for (var property : properties) {
            var name = property.path("name").asText("");
            if (!"PropertyValue".equals(property.path("@type").asText("")) || !seen.add(name)
                    || !Objects.equals(expected.get(name), property.path("value").asText(""))) return Optional.empty();
        }
        if (!seen.equals(expected.keySet())) return Optional.empty();
        if (!"Product".equals(variant.path("@type").asText(""))
                || !"cafe24_dnyint_6_2162_P0000DFE000H".equals(variant.path("sku").asText(""))
                || !"[핫딜] Jabra Evolve2 Buds USB-A타입-UC-미포함".equals(Jsoup.parse(variant.path("name").asText("")).text())
                || !"Offer".equals(offer.path("@type").asText("")) || !"KRW".equals(offer.path("priceCurrency").asText(""))
                || !"https://schema.org/InStock".equals(offer.path("availability").asText("")) || !productUrl(offer.path("url").asText(""), true)
                || offer.has("priceSpecification") || offer.has("eligibleCustomerType") || offer.has("validForMemberTier")
                || offer.has("eligibleQuantity") || offer.has("businessFunction")
                || offer.has("itemCondition") && !"https://schema.org/NewCondition".equals(offer.path("itemCondition").asText(""))) return Optional.empty();
        var rawRegular = offer.path("price").asText("");
        if (!rawRegular.matches("[1-9][0-9]{0,7}")) return Optional.empty();
        var regular = new BigDecimal(rawRegular);
        try {
            if (LocalDateTime.parse(offer.path("priceValidUntil").asText(""), DATE).atZone(ZoneId.of("Asia/Seoul")).toInstant().isBefore(now.plus(Duration.ofHours(72)))) return Optional.empty();
        } catch (RuntimeException e) { return Optional.empty(); }
        var roots = doc.select(".xans-product-detail"); if (roots.size() != 1) return Optional.empty();
        var root = roots.first();
        var reg = root.select("#span_product_price_text"); var sale = root.select("#span_product_price_sale");
        var periods = root.select("tr[rel='할인 기간'] .period");
        if (reg.size() != 1 || sale.size() != 1 || periods.size() != 1 || !visible(reg.first()) || !visible(sale.first())) return Optional.empty();
        var row = sale.first().closest("tr");
        if (row == null || !"할인가".equals(row.attr("rel")) || row.text().matches("(?is).*(회원|쿠폰|카드|구독|월\\s*납|member|coupon).*")) return Optional.empty();
        BigDecimal discounted;
        try {
            var regText = reg.first().text().replace("￦", "").replace(",", "").strip();
            var saleText = sale.first().text().replace("￦", "").replace(",", "").strip();
            if (!regText.matches("[1-9][0-9]{0,7}") || !saleText.matches("[1-9][0-9]{0,7}") || regular.compareTo(new BigDecimal(regText)) != 0) return Optional.empty();
            discounted = new BigDecimal(saleText);
            var range = periods.first().text().split("\\s*~\\s*", -1);
            if (range.length != 2 || discounted.signum() <= 0 || discounted.compareTo(regular) > 0) return Optional.empty();
            var start = LocalDateTime.parse(range[0], DATE).atZone(ZoneId.of("Asia/Seoul")).toInstant();
            var end = LocalDateTime.parse(range[1], DATE).atZone(ZoneId.of("Asia/Seoul")).toInstant();
            if (start.isAfter(now) || end.isBefore(now.plus(Duration.ofHours(72)))) return Optional.empty();
        } catch (RuntimeException e) { return Optional.empty(); }
        var selects = root.select("select[product_type=product_option]");
        if (selects.size() != 3 || !selects.stream().map(e -> e.attr("option_title")).toList().equals(List.of("USB타입", "플랫폼", "Qi 무선충전패드"))
                || selects.stream().anyMatch(e -> !"2162".equals(e.attr("option_product_no")))) return Optional.empty();
        if (selects.get(0).select("option[value='USB-A타입']:not([disabled])").size() != 1
                || selects.get(1).select("option[value=UC]").size() != 1
                || selects.get(2).select("option[value=미포함]").size() != 1) return Optional.empty();
        var buys = root.select("#actionBuy").stream().filter(JabraEvolve2PriceVerifier::visible).toList();
        if (buys.isEmpty() || buys.size() > 2 || buys.stream().anyMatch(e -> !"바로구매".equals(e.text()) || e.closest("a.btnSubmit") == null
                || !"#none".equals(e.closest("a.btnSubmit").attr("href")))) return Optional.empty();
        String stock = null; var prices = new ArrayList<String>();
        for (var script : doc.select("script:not([src])")) {
            var text = script.data(); var prefix = "var option_stock_data = '"; int index = text.indexOf(prefix);
            if (index >= 0) {
                if (stock != null || text.indexOf(prefix, index + prefix.length()) >= 0) return Optional.empty();
                int from = index + prefix.length(); int end = text.indexOf("';", from);
                if (end < 0 || end - from > 100_000) return Optional.empty(); stock = text.substring(from, end);
            }
            var m = SALE.matcher(text); while (m.find()) prices.add(m.group(1));
        }
        if (stock == null || prices.isEmpty() || prices.stream().anyMatch(p -> p.length() > 8 || discounted.compareTo(new BigDecimal(p)) != 0)) return Optional.empty();
        try {
            var item = JSON.readTree(stock.replace("\\\\", "\\").replace("\\\"", "\"").replace("\\/", "/")).path(CODE);
            if (!"USB-A타입-UC-미포함".equals(item.path("option_value").asText("")) || !"T".equals(item.path("is_selling").asText(""))
                    || !"T".equals(item.path("is_display").asText("")) || !"F".equals(item.path("is_auto_soldout").asText(""))
                    || !"N".equals(item.path("is_reserve_stat").asText("")) || !item.path("use_stock").asBoolean(false)
                    || new BigDecimal(item.path("option_price").asText("-1")).compareTo(regular) != 0
                    || new BigDecimal(item.path("stock_price").asText("-1")).signum() != 0
                    || new BigDecimal(item.path("origin_option_added_price").asText("-1")).signum() != 0) return Optional.empty();
        } catch (RuntimeException e) { return Optional.empty(); }
        return Optional.of(discounted);
    }

    private static boolean productUrl(String value, boolean offer) {
        try { var uri = URI.create(value);
            return page(value) && "https".equals(uri.getScheme()) && Set.of("jabramall.com", "www.jabramall.com").contains(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1 && SLUG.equals(uri.getPath()) && uri.getFragment() == null
                    && (offer ? ("item_code=" + CODE).equals(uri.getRawQuery()) : uri.getRawQuery() == null);
        } catch (RuntimeException e) { return false; }
    }
    static boolean page(String value) {
        try {
            var uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || !Set.of("jabramall.com", "www.jabramall.com").contains(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getFragment() != null
                    || uri.getRawPath().toLowerCase(Locale.ROOT).matches(".*%(?:2f|5c|2e|25).*")
                    || !uri.normalize().getPath().equals(uri.getPath())) return false;
            if ("/product/detail.html".equals(uri.getPath())) return "product_no=2162".equals(uri.getRawQuery());
            return SLUG.equals(uri.getPath()) && (uri.getRawQuery() == null || ("item_code=" + CODE).equals(uri.getRawQuery()));
        } catch (RuntimeException e) { return false; }
    }
    private static boolean visible(Element e) {
        for (var node = e; node != null; node = node.parent()) {
            var style = node.attr("style").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            if (node.hasAttr("disabled") || node.hasAttr("hidden") || node.hasClass("displaynone") || node.hasClass("disabled")
                    || "true".equalsIgnoreCase(node.attr("aria-hidden")) || "true".equalsIgnoreCase(node.attr("aria-disabled"))
                    || style.contains("display:none") || style.contains("visibility:hidden")) return false;
        }
        return true;
    }
}
