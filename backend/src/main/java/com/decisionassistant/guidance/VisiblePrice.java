package com.decisionassistant.guidance;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** JSON-LD 금액과 같은 구매 영역의 가격을 대조한다. 다른 가격으로 대체하지 않는다. */
final class VisiblePrice {
    private VisiblePrice() {}
    private static final Pattern MONEY = Pattern.compile("(?<![0-9,])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\s*(?:원|KRW|₩)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONDITIONAL = Pattern.compile("(?i)회원|멤버|쿠폰|카드|혜택|구독|월\\s*납|member|coupon|cashback|subscription");
    private static final Pattern SALE = Pattern.compile("(?i)판매가|판매 가격|구매가|상품 가격|결제금액|sale price|selling price|price");
    record Result(String quote, OfficialProductParser.Rejection rejection) {}
    static Result inspect(Document doc, String url, String name, String sku, BigDecimal price) {
        var rows = new LinkedHashSet<String>();
        for (var root : ProductPageRegions.products(doc, name, url, sku)) {
            for (var row : root.select("tr, dl > div, [itemprop=offers]")) {
                if (ProductPageRegions.owned(row, root) && row.text().length() <= 600) rows.add(row.text());
            }
        }
        if (ProductPageRegions.current(doc, url, name)) {
            var store = OfficialStorePolicy.store(url).orElseThrow();
            var selector = switch (store.id()) {
                case "qcy-kr" -> ".xans-product-detail tr.product_price_css, .xans-product-detail tr.product_sale_price_css";
                case "lg-kr" -> ".pricing_info_box [class*=pricing_info_item_inner]";
                default -> "";
            };
            if (!selector.isEmpty()) for (var row : doc.select(selector)) if (row.text().length() <= 600) rows.add(row.text());
        }
        // Apple 구매 링크의 SKU와 현재 상품의 표시 가격을 함께 대조한다.
        if (OfficialStorePolicy.store(url).orElseThrow().id().equals("apple-kr") && ProductPageRegions.current(doc, url, name)) {
            var links = doc.select(".product-selection-area a[data-slot-name=productSelection]");
            // AirPods 5의 충전 케이스 구성은 선택된 항목만 해당 SKU·URL과 대조한다.
            if (links.size() > 1 && java.net.URI.create(url).getPath().equals("/kr/shop/buy-airpods/airpods-5/무선-충전-케이스-포함")) {
                var areas = doc.select(".product-selection-area");
                var selections = doc.select(".product-selection-area .item.selection");
                if (areas.size() == 1 && selections.size() == 1)
                    links = selections.first().select("a[data-slot-name=productSelection]");
            }
            if (links.size() == 1) {
                var link = links.getFirst();
                var target = java.net.URI.create(link.absUrl("href"));
                var selected = Arrays.stream(Objects.toString(target.getQuery(), "").split("&"))
                        .filter(item -> item.startsWith("product=")).map(item -> item.substring(8)).toList();
                var prices = link.select(".current_price");
                if (OfficialProductParser.samePage(url, target.toString()) && selected.size() == 1
                        && !sku.isBlank() && selected.getFirst().equalsIgnoreCase(sku) && prices.size() == 1
                        && !CONDITIONAL.matcher(link.text()).find()) {
                    var text = prices.getFirst().text();
                    if (text.matches("₩[0-9]{1,3}(?:,[0-9]{3})*")) {
                        var amount = new BigDecimal(text.substring(1).replace(",", ""));
                        if (amount.compareTo(price) == 0) return new Result("구매 선택 판매가 " + text, null);
                    }
                }
            }
        }
        String confirmed = null;
        for (var text : rows) {
            var amounts = new HashSet<BigDecimal>();
            var matcher = MONEY.matcher(text);
            while (matcher.find()) amounts.add(new BigDecimal(matcher.group(1).replace(",", "")).stripTrailingZeros());
            if (!amounts.contains(price.stripTrailingZeros())) continue;
            if (CONDITIONAL.matcher(text).find()) return new Result(null, OfficialProductParser.Rejection.CONDITIONAL_PRICE);
            if (amounts.size() == 1 && SALE.matcher(text).find() && !text.matches(".*(?:소비자가|정가|할인 전|권장가).*")) confirmed = text;
        }
        return confirmed == null ? new Result(null, OfficialProductParser.Rejection.PRICE_CONTEXT_MISSING) : new Result(confirmed, null);
    }
}
