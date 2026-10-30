package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/** 앤커코리아 상품 43의 단일 할인 판매가와 색상별 구성을 확인한다. */
final class AnkerProductVerifier {
    static final String URL = "https://ankerkorea.co.kr/product/detail.html?product_no=43";
    static final String NAME = "앤커 사운드코어 P31i(R60i NC) 노이즈캔슬링 무선 블루투스 이어폰 D1202";
    static final String AERO_URL = "https://ankerkorea.co.kr/product/detail.html?product_no=45";
    static final String AERO_NAME = "앤커 사운드코어 에어로클립 오픈형 블루투스이어폰 A3388";
    static final String LIBERTY_URL = "https://ankerkorea.co.kr/product/detail.html?product_no=40";
    static final String LIBERTY_NAME = "앤커 사운드코어 리버티5 노이즈캔슬링 블루투스 이어폰 A3957";
    private record Product(String url, String name, String number, String option, Map<String, String> colors) {}
    private static final Product P31I = new Product(URL, NAME, "43", "색상선택", Map.of("블랙", "P00000BR000B", "블루", "P00000BR000C", "핑크", "P00000BR000D", "화이트", "P00000BR000E"));
    private static final Product AERO = new Product(AERO_URL, AERO_NAME, "45", "색상선택", Map.of("블랙", "P00000BT000B", "화이트", "P00000BT000C", "핑크", "P00000BT000D", "블루", "P00000BT000E"));
    private static final Product LIBERTY = new Product(LIBERTY_URL, LIBERTY_NAME, "40", "옵션선택", Map.of("블랙", "P00000BO000B", "화이트", "P00000BO000C"));
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern SALE = Pattern.compile("\\bvar\\s+product_sale_price\\s*=\\s*([0-9]+)\\s*;");
    private AnkerProductVerifier() {}

    static Optional<BigDecimal> price(String html, Instant now) {
        return price(html, now, P31I);
    }
    static Optional<BigDecimal> aeroclipPrice(String html, Instant now) {
        return price(html, now, AERO);
    }
    static Optional<BigDecimal> libertyPrice(String html, Instant now) {
        return price(html, now, LIBERTY);
    }
    private static Optional<BigDecimal> price(String html, Instant now, Product product) {
        if (html == null || now == null || html.length() > 2_000_000) return Optional.empty();
        var doc = Jsoup.parse(html, product.url());
        var canonicals = doc.select("link[rel=canonical]");
        if (canonicals.size() != 1 || !OfficialProductParser.samePage(product.url(), canonicals.first().absUrl("href"))) return Optional.empty();
        var groups = new ArrayList<JsonNode>();
        for (var script : doc.select("script[type=application/ld+json]")) {
            try { var item = JSON.readTree(script.data()); if ("ProductGroup".equals(item.path("@type").asText(""))) groups.add(item); }
            catch (RuntimeException ignored) { return Optional.empty(); }
        }
        if (groups.size() != 1) return Optional.empty();
        var group = groups.getFirst();
        if (!product.name().equals(group.path("name").asText("")) || !("cafe24_ankerkr_1_" + product.number()).equals(group.path("productGroupID").asText(""))
                || !group.path("variesBy").isArray() || group.path("variesBy").size() != 1
                || !product.option().equals(group.path("variesBy").get(0).asText("")) || !group.path("hasVariant").isArray()
                || group.path("hasVariant").size() != product.colors().size()) return Optional.empty();
        var seen = new HashSet<String>(); var prices = new HashSet<BigDecimal>();
        for (var variant : group.path("hasVariant")) {
            var properties = variant.path("additionalProperty");
            if (!properties.isArray() || properties.size() != 1 || !"PropertyValue".equals(properties.get(0).path("@type").asText(""))
                    || !product.option().equals(properties.get(0).path("name").asText(""))) return Optional.empty();
            var color = properties.get(0).path("value").asText(""); var code = product.colors().get(color); var offer = variant.path("offers");
            if (code == null || !seen.add(color) || !"Product".equals(variant.path("@type").asText(""))
                    || !(product.name() + " " + color).equals(variant.path("name").asText(""))
                    || !("cafe24_ankerkr_1_" + product.number() + "_" + code).equals(variant.path("sku").asText(""))
                    || !"Offer".equals(offer.path("@type").asText("")) || !"KRW".equals(offer.path("priceCurrency").asText(""))
                    || !"https://schema.org/InStock".equals(offer.path("availability").asText(""))
                    || offer.has("priceSpecification") || offer.has("eligibleCustomerType") || offer.has("eligibleQuantity")
                    || offer.has("validForMemberTier") || offer.has("businessFunction")
                    || offer.has("itemCondition") && !"https://schema.org/NewCondition".equals(offer.path("itemCondition").asText(""))) return Optional.empty();
            try {
                var uri = URI.create(offer.path("url").asText(""));
                if (!OfficialProductParser.samePage(product.url(), uri.toString()) || !("item_code=" + code).equals(uri.getRawQuery()) || uri.getFragment() != null)
                    return Optional.empty();
                var expires = LocalDateTime.parse(offer.path("priceValidUntil").asText(""), DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"));
                if (expires.atZone(ZoneId.of("Asia/Seoul")).toInstant().isBefore(now.plus(Duration.ofHours(24)))) return Optional.empty();
                var raw = offer.path("price").asText("");
                if (!raw.matches("[1-9][0-9]{0,7}")) return Optional.empty();
                prices.add(new BigDecimal(raw));
            } catch (RuntimeException ignored) { return Optional.empty(); }
        }
        if (prices.size() != 1) return Optional.empty();
        var regular = prices.iterator().next();
        var selections = doc.select(".xans-product-detail select[product_type=product_option]");
        if (selections.size() != 1 || !product.number().equals(selections.first().attr("option_product_no"))
                || !product.option().equals(selections.first().attr("option_title")) || selections.first().hasAttr("disabled")) return Optional.empty();
        var options = new HashSet<String>();
        for (var option : selections.first().select("option")) {
            var value = option.attr("value");
            if ("*".equals(value) || "**".equals(value)) continue;
            if (option.hasAttr("disabled") || !product.colors().containsKey(value) || !value.equals(option.text()) || !options.add(value)) return Optional.empty();
        }
        if (!options.equals(product.colors().keySet())) return Optional.empty();
        var regulars = doc.select(".xans-product-detail #span_product_price_text");
        var sales = doc.select(".xans-product-detail #span_optimum_discount_price");
        var buttons = doc.select(".xans-product-detail .productAction #actionBuy");
        if (regulars.size() != 1 || sales.size() != 1 || buttons.size() != 1 || !"구매하기".equals(buttons.first().text())
                || !visible(regulars.first()) || !visible(sales.first()) || !visible(buttons.first())) return Optional.empty();
        var row = sales.first().closest("tr");
        if (row == null || row.select("th").size() != 1 || !"할인가".equals(row.select("th").first().text())
                || row.text().matches("(?is).*(회원|쿠폰|카드|구독|월\\s*납|member|coupon).*")) return Optional.empty();
        var regularRaw = regulars.first().ownText().strip(); var saleRaw = sales.first().ownText().strip();
        if (!regularRaw.matches("[0-9,]+원") || !saleRaw.matches("[0-9,]+원")) return Optional.empty();
        BigDecimal sale;
        try {
            if (regular.compareTo(new BigDecimal(regularRaw.replace(",", "").replace("원", ""))) != 0) return Optional.empty();
            sale = new BigDecimal(saleRaw.replace(",", "").replace("원", ""));
            if (sale.signum() <= 0 || sale.compareTo(regular) > 0) return Optional.empty();
        } catch (NumberFormatException ignored) { return Optional.empty(); }
        String stock = null; var saleValues = new ArrayList<String>();
        for (var script : doc.select("script:not([src])")) {
            var source = script.data(); var prefix = "var option_stock_data = '"; var start = source.indexOf(prefix);
            if (start >= 0) {
                if (stock != null || source.indexOf(prefix, start + prefix.length()) >= 0) return Optional.empty();
                start += prefix.length(); var end = source.indexOf("';", start);
                if (end < 0 || end - start > 100_000) return Optional.empty();
                stock = source.substring(start, end);
            }
            var pm = SALE.matcher(script.data()); while (pm.find()) saleValues.add(pm.group(1));
        }
        if (stock == null || saleValues.isEmpty() || saleValues.stream().anyMatch(value -> value.length() > 8 || new BigDecimal(value).compareTo(sale) != 0)) return Optional.empty();
        try {
            var entries = JSON.readTree(stock.replace("\\\\", "\\").replace("\\\"", "\"").replace("\\/", "/"));
            if (!entries.isObject() || entries.size() != product.colors().size()) return Optional.empty();
            for (var entry : product.colors().entrySet()) {
                var item = entries.path(entry.getValue());
                if (!entry.getKey().equals(item.path("option_value").asText("")) || !"T".equals(item.path("is_selling").asText(""))
                        || !"T".equals(item.path("is_display").asText("")) || !"F".equals(item.path("is_auto_soldout").asText(""))
                        || !"N".equals(item.path("is_reserve_stat").asText(""))
                        || new BigDecimal(item.path("option_price").asText("-1")).compareTo(regular) != 0
                        || new BigDecimal(item.path("stock_price").asText("-1")).signum() != 0
                        || new BigDecimal(item.path("origin_option_added_price").asText("-1")).signum() != 0) return Optional.empty();
            }
        } catch (RuntimeException ignored) { return Optional.empty(); }
        return Optional.of(sale);
    }
    private static boolean visible(org.jsoup.nodes.Element element) {
        for (var e = element; e != null; e = e.parent()) {
            var style = e.attr("style").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            if (e.hasAttr("disabled") || e.hasAttr("hidden") || e.hasClass("disabled") || e.hasClass("displaynone")
                    || "true".equalsIgnoreCase(e.attr("aria-disabled")) || "true".equalsIgnoreCase(e.attr("aria-hidden"))
                    || style.contains("display:none") || style.contains("visibility:hidden")) return false;
        }
        return true;
    }
}
