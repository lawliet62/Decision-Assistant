package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/** 상품 91의 단일 상품 구성과 비회원 할인가를 확인한다. */
final class AnkerP41iVerifier {
    static final String URL = "https://ankerkorea.co.kr/product/detail.html?product_no=91";
    static final String NAME = "앤커 사운드코어 P41i 노이즈캔슬링 블루투스이어폰 A3937";
    static final String TITLE = "P41i 노이즈캔슬링 블루투스 이어폰 2 in 1 보조배터리 3000mAh A3937";
    static final String SPEC_URL = "https://www.ankerjapan.com/products/a3937";
    static final String QUOTE = "周囲の騒音レベルに応じてノイズキャンセリングの強さを自動で調節";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private AnkerP41iVerifier() {}

    static boolean specification(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, SPEC_URL); var canonical = doc.select("link[rel=canonical]"); var headings = doc.select("h1");
        if (canonical.size() != 1 || !SPEC_URL.equals(canonical.first().absUrl("href")) || headings.size() != 1
                || !"Soundcore P41i".equals(headings.first().text())
                || !"Soundcore P41i | 完全ワイヤレスイヤホンの製品情報 | Anker Japan 公式オンラインストア".equals(doc.title())) return false;
        return doc.select(".pfs-text").stream().filter(e -> e.select("h3").size() == 1
                && "ウルトラノイズキャンセリング 2.0".equals(e.select("h3").first().text())
                && e.select("p").size() == 1 && e.select("p").first().text().startsWith(QUOTE + "し、")).count() == 1;
    }
    private static Optional<tools.jackson.databind.JsonNode> product(String html) {
        if (html == null || html.length() > 2_000_000) return Optional.empty();
        var doc = Jsoup.parse(html, URL);
        var canonical = doc.select("link[rel=canonical]"); var headings = doc.select("h1");
        if (canonical.size() != 1 || !OfficialProductParser.samePage(URL, canonical.first().absUrl("href"))
                || headings.size() != 1 || !NAME.equals(headings.first().text()) || !TITLE.equals(doc.title())) return Optional.empty();
        var products = new ArrayList<tools.jackson.databind.JsonNode>();
        for (var script : doc.select("script[type=application/ld+json]")) {
            try { var item = JSON.readTree(script.data()); if ("Product".equals(item.path("@type").asText(""))) products.add(item); }
            catch (RuntimeException ignored) { return Optional.empty(); }
        }
        if (products.size() != 1 || !NAME.equals(products.getFirst().path("name").asText(""))
                || !"cafe24_ankerkr_1_91".equals(products.getFirst().path("sku").asText(""))) return Optional.empty();
        return Optional.of(products.getFirst());
    }
    static Optional<BigDecimal> price(String html, Instant now) {
        var item = product(html).orElse(null);
        if (item == null || now == null) return Optional.empty();
        var offer = item.path("offers");
        if (!"Offer".equals(offer.path("@type").asText("")) || !"KRW".equals(offer.path("priceCurrency").asText(""))
                || !"https://schema.org/InStock".equals(offer.path("availability").asText(""))
                || !OfficialProductParser.samePage(URL, offer.path("url").asText(""))
                || offer.has("priceSpecification") || offer.has("eligibleCustomerType") || offer.has("eligibleQuantity")
                || offer.has("validForMemberTier") || offer.has("businessFunction")
                || offer.has("itemCondition") && !"https://schema.org/NewCondition".equals(offer.path("itemCondition").asText(""))) return Optional.empty();
        BigDecimal regular;
        try {
            var offerUrl = URI.create(offer.path("url").asText(""));
            if (offerUrl.getRawQuery() != null || offerUrl.getFragment() != null) return Optional.empty();
            var expiry = LocalDateTime.parse(offer.path("priceValidUntil").asText(""), DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"));
            if (expiry.atZone(ZoneId.of("Asia/Seoul")).toInstant().isBefore(now.plus(Duration.ofHours(24)))) return Optional.empty();
            var raw = offer.path("price").asText("");
            if (!raw.matches("[1-9][0-9]{0,7}")) return Optional.empty();
            regular = new BigDecimal(raw);
        } catch (RuntimeException ignored) { return Optional.empty(); }
        var doc = Jsoup.parse(html, URL);
        if (!doc.select(".xans-product-detail select[product_type=product_option]").isEmpty()) return Optional.empty();
        var regulars = doc.select(".xans-product-detail #span_product_price_text");
        var sales = doc.select(".xans-product-detail #span_optimum_discount_price");
        var buttons = doc.select(".xans-product-detail .productAction #actionBuy");
        if (regulars.size() != 1 || sales.size() != 1 || buttons.size() != 1 || !"구매하기".equals(buttons.first().text())
                || !visible(regulars.first()) || !visible(sales.first()) || !visible(buttons.first())) return Optional.empty();
        var row = sales.first().closest("tr");
        if (row == null || row.select("th").size() != 1 || !"할인가".equals(row.select("th").first().text())
                || row.text().matches("(?is).*(회원|쿠폰|카드|구독|월\\s*납|member|coupon).*")) return Optional.empty();
        var reg = regulars.first().ownText().strip(); var rawSale = sales.first().ownText().strip();
        if (!reg.matches("[0-9,]+원") || !rawSale.matches("[0-9,]+원")) return Optional.empty();
        BigDecimal sale;
        try {
            if (regular.compareTo(new BigDecimal(reg.replace(",", "").replace("원", ""))) != 0) return Optional.empty();
            sale = new BigDecimal(rawSale.replace(",", "").replace("원", ""));
            if (sale.signum() <= 0 || sale.compareTo(regular) > 0) return Optional.empty();
        } catch (RuntimeException ignored) { return Optional.empty(); }
        var stockPattern = Pattern.compile("\\bvar\\s+single_option_stock_data\\s*=\\s*'([^']*)'\\s*;");
        var salePattern = Pattern.compile("\\bvar\\s+product_sale_price\\s*=\\s*([0-9]+)\\s*;");
        var productCode = Pattern.compile("\\bvar\\s+product_code\\s*=\\s*'([^']*)'\\s*;");
        var itemCode = Pattern.compile("\\bvar\\s+item_code\\s*=\\s*'([^']*)'\\s*;");
        var stocks = new ArrayList<String>(); var salesFound = new ArrayList<String>();
        var productsFound = new ArrayList<String>(); var itemsFound = new ArrayList<String>();
        for (var script : doc.select("script:not([src])")) {
            var m = stockPattern.matcher(script.data()); while (m.find()) stocks.add(m.group(1));
            m = salePattern.matcher(script.data()); while (m.find()) salesFound.add(m.group(1));
            m = productCode.matcher(script.data()); while (m.find()) productsFound.add(m.group(1));
            m = itemCode.matcher(script.data()); while (m.find()) itemsFound.add(m.group(1));
        }
        if (stocks.size() != 1 || !productsFound.equals(List.of("P00000DN")) || !itemsFound.equals(List.of("P00000DN000A"))
                || salesFound.isEmpty() || salesFound.stream().anyMatch(s -> s.length() > 8 || new BigDecimal(s).compareTo(sale) != 0)) return Optional.empty();
        try {
            var stock = JSON.readTree(stocks.getFirst().replace("\\\\", "\\").replace("\\\"", "\"").replace("\\/", "/"));
            if (!stock.path("use_stock").asBoolean(false) || !"T".equals(stock.path("use_soldout").asText(""))
                    || !stock.path("stock_number").isIntegralNumber() || stock.path("stock_number").asInt() <= 0
                    || !"N".equals(stock.path("is_reserve_stat").asText(""))) return Optional.empty();
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
