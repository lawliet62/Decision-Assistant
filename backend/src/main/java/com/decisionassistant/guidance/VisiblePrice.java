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
