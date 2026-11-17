package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/** 고정 상품의 선택된 글래스 블루 1개 가격만 확인한다. */
final class SonyC710nVerifier {
    static final String URL = "https://store.sony.co.kr/product-view/128838586";
    static final String IDENTITY = "sony-kr:128838586";
    static final String NAME = "WF-C710N 글래스 블루";
    static final String OPTION = "WF-C710N / 글래스 블루";
    static final String SPEC_URL = "https://www.sony.co.kr/headphones/products/wf-c710n";
    static final String CONNECTION_URL = "https://www.sony.co.kr/electronics/support/software/00369642";
    static final String ANC_QUOTE = "듀얼 마이크가 주변 소리를 감지하여 WF-C710N의 노이즈 캔슬링 기능을 개선";
    static final String CONNECTION_QUOTE = "헤드셋이 Android 장치 또는 iPhone에 연결되어야 합니다.";
    private SonyC710nVerifier() {}

    static boolean supports(DiscoveryContracts.ProductFact fact) {
        return "ANC 지원".equals(fact.name()) && "지원".equals(fact.value())
                && SPEC_URL.equals(fact.sourceUrl()) && ANC_QUOTE.equals(fact.quote())
                || "호환성".equals(fact.name()) && "Android/iPhone".equals(fact.value())
                && CONNECTION_URL.equals(fact.sourceUrl()) && CONNECTION_QUOTE.equals(fact.quote());
    }

    static boolean specification(String bundle, DiscoveryContracts.ProductFact fact) {
        if (!supports(fact) || bundle == null || bundle.length() > 4_100_000) return false;
        try {
            var pages = tools.jackson.databind.json.JsonMapper.builder().build().readTree(bundle);
            var doc = Jsoup.parse(pages.path(fact.sourceUrl()).asText(""), fact.sourceUrl());
            var canonical = doc.select("link[rel=canonical]");
            if (canonical.size() != 1 || !fact.sourceUrl().equals(canonical.first().absUrl("href"))) return false;
            var expectedTitle = SPEC_URL.equals(fact.sourceUrl())
                    ? "WF-C710N 무선 노이즈 캔슬링 이어폰 | 30시간 배터리 | 소니코리아"
                    : "펌웨어 업데이트 버전 1.0.5 | Sony KR";
            if (!expectedTitle.equals(doc.title())) return false;
            return doc.text().contains(fact.quote()) && (SPEC_URL.equals(fact.sourceUrl())
                    || doc.text().contains("WF-C710N용 펌웨어 업데이트 1.0.5"));
        } catch (RuntimeException e) { return false; }
    }

    static Optional<BigDecimal> price(String html) {
        if (html == null || html.length() > 2_000_000) return rejected("HTML_MISSING_OR_TOO_LARGE");
        var doc = Jsoup.parse(html, URL);
        var roots = doc.select(".product_view_about");
        if (roots.size() != 1 || !ProductPageRegions.current(doc, URL, "WF-C710N")) return rejected("PRODUCT_REGION_OR_PAGE_IDENTITY");
        var names = roots.first().select(".product_tit");
        if (names.size() != 1 || !"WF-C710N".equals(names.first().text())) return rejected("MODEL_NAME");
        var sales = roots.first().select(".product_price .sale");
        if (sales.size() != 1 || sales.first().select(".price").size() != 1) return rejected("SALE_AREA_AMBIGUOUS");
        var raw = sales.first().selectFirst(".price").text();
        if (!raw.matches("[0-9]{1,3}(?:,[0-9]{3})+")
                || !sales.first().text().replaceAll("\\s+", "").equals(raw + "원")) return rejected("SALE_FORMAT_OR_CONDITIONS");

        // 선택 목록을 단일 구성·수량·금액에 연결한다. 기본 표시가만으로 통과하지 않는다.
        var selected = doc.select(".selected_opt");
        if (selected.size() != 1 || selected.first().select(".opt_info").size() != 1) return rejected("SELECTED_CONFIGURATION_MISSING");
        var item = selected.first().selectFirst(".opt_info");
        var labels = item.select(".opt_name");
        var counts = item.select("input.count");
        var amounts = item.select(".opt_price");
        if (labels.size() != 1 || !OPTION.equals(labels.first().text()) || counts.size() != 1
                || !"1".equals(counts.first().val()) || amounts.size() != 1
                || !amounts.first().text().replaceAll("\\s+", "").equals(raw + "원")) return rejected("SELECTED_LABEL_QUANTITY_OR_PRICE");
        var choices = doc.select("a.opt_list");
        var expected = Set.of(OPTION, "WF-C710N / 블랙", "WF-C710N / 화이트", "WF-C710N / 핑크");
        if (choices.size() != 4 || !Set.copyOf(choices.eachText()).equals(expected)) {
            org.slf4j.LoggerFactory.getLogger(SonyC710nVerifier.class).warn(
                    "sony_c710n_option_list count={} distinct={} expectedMatches={}", choices.size(),
                    choices.eachText().stream().distinct().count(),
                    choices.stream().filter(e -> expected.contains(e.text())).count());
            return rejected("OPTION_LIST_CHANGED");
        }
        var chosen = choices.stream().filter(e -> OPTION.equals(e.text())).findFirst().orElseThrow();
        if (unavailable(chosen.className() + " " + chosen.text()) || chosen.hasAttr("disabled")
                || "true".equals(chosen.attr("aria-disabled"))) return rejected("SELECTED_OPTION_UNAVAILABLE");
        var buy = roots.first().select(".result_btn_box a.direct").stream()
                .filter(e -> "https://store.sony.co.kr/order/sheet".equals(e.absUrl("href"))).toList();
        if (buy.size() != 1 || buy.getFirst().hasAttr("disabled") || "true".equals(buy.getFirst().attr("aria-disabled"))
                || unavailable(buy.getFirst().className() + " " + buy.getFirst().text())) return rejected("PURCHASE_UNAVAILABLE");
        var price = new BigDecimal(raw.replace(",", ""));
        return price.signum() > 0 && price.compareTo(new BigDecimal("100000000")) <= 0
                ? Optional.of(price) : rejected("PRICE_RANGE");
    }

    private static Optional<BigDecimal> rejected(String stage) {
        org.slf4j.LoggerFactory.getLogger(SonyC710nVerifier.class).warn("sony_c710n_price_check stage={}", stage);
        return Optional.empty();
    }

    private static boolean unavailable(String value) {
        return value.matches("(?is).*(disabled|soldout|sold\\s*out|품절|판매종료|구매불가).*" );
    }
}
