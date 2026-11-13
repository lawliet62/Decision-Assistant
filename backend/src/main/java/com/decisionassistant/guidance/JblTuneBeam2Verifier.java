package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.Optional;

/** 삼성닷컴의 고정 JBL 블랙 SKU만 확인한다. 최대 혜택가를 일반 판매가로 승격하지 않는다. */
final class JblTuneBeam2Verifier {
    static final String URL = "https://www.samsung.com/sec/harman-life-style-audio/jbl-earphone-tunebeam2-d2c/JBLTBEAM2BLKAS/";
    static final String SKU = "JBLTBEAM2BLKAS";
    static final String NAME = "JBL TUNE BEAM 2 어댑티브 노이즈 캔슬링 무선 이어폰";
    static final String ID = "jbl-tune-beam2-black";
    static final String IDENTITY = "jbl-samsung-kr:" + SKU;
    static final String SPEC_URL = "https://kr.jbl.com/TUNE-BEAM-2.html";
    static final String SPEC_TITLE = "JBL Tune Beam 2 | 트루 와이어리스 노이즈 캔슬링 이어버드";
    static final String ANC_QUOTE = "ANC 기능을 켠 상태";
    static final String ANDROID_QUOTE = "JBL Tune Beam 2는 Google의 빠른 페어링 기능을 지원하므로 한 번의 탭으로 Android 장치와 페어링할 수 있습니다.";
    private JblTuneBeam2Verifier() {}

    static Optional<BigDecimal> price(String html) {
        if (html == null || html.length() > 2_000_000) return rejected("HTML_SIZE");
        try {
            var doc = Jsoup.parse(html, URL);
            if (!canonical(doc, URL) || !doc.title().equals(NAME + " | " + SKU + " | Samsung 대한민국")) return rejected("PAGE_IDENTITY");
            var products = new java.util.ArrayList<tools.jackson.databind.JsonNode>();
            for (var script : doc.select("script[type=application/ld+json]")) {
                if (script.data().length() > 200_000) return rejected("JSON_SIZE");
                var node = JsonMapper.builder().build().readTree(script.data());
                if (node.path("@type").isString() && "Product".equals(node.path("@type").asText())) products.add(node);
            }
            if (products.size() != 1) return rejected("PRODUCT_COUNT");
            var product = products.getFirst();
            var offer = product.path("offers");
            if (!SKU.equals(product.path("sku").asText()) || !NAME.equals(product.path("name").asText())
                    || !URL.equals(product.path("@id").asText()) || !offer.isObject()
                    || !"Offer".equals(offer.path("@type").asText()) || !URL.equals(offer.path("url").asText())
                    || !"KRW".equals(offer.path("priceCurrency").asText())
                    || !"https://schema.org/InStock".equals(offer.path("availability").asText())) return rejected("OFFER_IDENTITY_OR_STOCK");
            for (var field : java.util.List.of("priceSpecification", "eligibleCustomerType", "eligibleQuantity", "validForMemberTier", "businessFunction", "priceValidUntil", "itemCondition"))
                if (offer.has(field)) return rejected("CONDITIONAL_OFFER");
            var raw = offer.path("price").asText();
            if (!raw.matches("[1-9][0-9]{0,7}")) return rejected("PRICE_FORMAT");
            var roots = doc.select("#goodsRevampOrderSummary");
            if (roots.size() != 1) return rejected("PURCHASE_REGION");
            var root = roots.first();
            if (root.select(".prod-title").size() != 1 || !NAME.equals(root.selectFirst(".prod-title").text())
                    || root.select(".prod-sku").size() != 1 || !SKU.equals(root.selectFirst(".prod-sku").text())) return rejected("SELECTED_SKU");
            var base = root.select(".summary-price-list dl.event-price");
            if (base.size() != 1 || base.first().select("dt").size() != 1 || !"기준가".equals(base.first().selectFirst("dt").text())
                    || base.first().select("dd span[data-price]").size() != 1) return rejected("BASE_PRICE_REGION");
            var amount = base.first().selectFirst("dd span[data-price]");
            if (!raw.equals(amount.attr("data-price")) || !raw.equals(amount.text().replace(",", ""))
                    || !base.first().selectFirst("dd").text().replaceAll("\\s+", "").equals(amount.text() + "원")) return rejected("BASE_PRICE_AMOUNT");
            // 렌더링된 총액·최대 혜택가도 기준가와 같을 때만 수용한다. hidden input은 수집기가 제거할 수 있다.
            for (var selector : java.util.List.of("#finalPrc", ".pd-total-price[name=totalPrice]")) {
                var totals = root.select(selector);
                if (totals.size() != 1 || !raw.equals(totals.first().text().replace(",", ""))) return rejected("DISPLAYED_TOTAL_OR_BENEFIT_DIFFERENT");
            }
            if (!input(doc, "countPrd", "1") || !doc.selectFirst("input#countPrd").attr("max").matches("[1-9][0-9]{0,5}")) return rejected("QUANTITY_OR_STOCK");
            var buttons = root.select("button#btnDirect[data-cta-type=normal]");
            if (buttons.size() != 1 || !"구매하기".equals(buttons.first().text()) || buttons.first().hasAttr("disabled")
                    || "true".equals(buttons.first().attr("aria-disabled"))
                    || (buttons.first().className() + " " + buttons.first().attr("style")).matches("(?is).*(disabled|soldout|display\\s*:\\s*none|visibility\\s*:\\s*hidden).*")) return rejected("PURCHASE_BUTTON");
            return Optional.of(new BigDecimal(raw));
        } catch (RuntimeException ex) { return rejected("PARSE_FAILED"); }
    }

    static boolean supports(DiscoveryContracts.ProductFact fact) {
        return SPEC_URL.equals(fact.sourceUrl()) && ("ANC 지원".equals(fact.name()) && "지원".equals(fact.value()) && ANC_QUOTE.equals(fact.quote())
                || "호환성".equals(fact.name()) && "Android".equals(fact.value()) && ANDROID_QUOTE.equals(fact.quote()));
    }

    static boolean specification(String html, DiscoveryContracts.ProductFact fact) {
        if (!supports(fact) || html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, SPEC_URL);
        return canonical(doc, SPEC_URL) && SPEC_TITLE.equals(doc.title())
                && doc.select("h1[itemprop=name]").size() == 1 && "JBL Tune Beam 2".equals(doc.selectFirst("h1[itemprop=name]").text())
                && doc.select(".feature-slide p").stream().anyMatch(p -> p.text().contains(fact.quote()) && !p.text().matches("(?is).*(미지원|지원하지\\s*않|not supported|unsupported).*" ));
    }

    private static Optional<BigDecimal> rejected(String stage) {
        org.slf4j.LoggerFactory.getLogger(JblTuneBeam2Verifier.class).warn("jbl_tunebeam2_price_check stage={}", stage);
        return Optional.empty();
    }
    private static boolean input(Document doc, String id, String value) {
        var nodes = doc.select("input#" + id);
        return nodes.size() == 1 && value.equals(nodes.first().val());
    }
    private static boolean canonical(Document doc, String url) {
        var nodes = doc.select("link[rel=canonical]");
        return nodes.size() == 1 && url.equals(nodes.first().absUrl("href"));
    }
}
