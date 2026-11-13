package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import tools.jackson.databind.json.JsonMapper;

/** 상품 731과 그 사양표의 단일 항목을 대조한다. */
final class SennheiserSpecificationVerifier {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String URL = "https://kr.sennheiser-hearing.com/product/detail.html?product_no=731";
    private SennheiserSpecificationVerifier() {}

    static boolean matches(String html, String field) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, URL);
        var canonical = doc.select("link[rel=canonical]");
        var headings = doc.select("h1");
        if (canonical.size() != 1 || !OfficialProductParser.samePage(URL, canonical.first().absUrl("href"))) return false;
        var name = SennheiserProductVerifier.nameForCanonical(canonical.first().absUrl("href")).orElse(null);
        if (name == null || headings.size() != 1 || !name.equals(headings.first().text())) return false;
        int groups = 0;
        for (var script : doc.select("script[type=application/ld+json]")) {
            try {
                var item = JSON.readTree(script.data());
                if ("ProductGroup".equals(item.path("@type").asText(""))) {
                    if (!name.equals(item.path("name").asText("")) || !"cafe24_bnispace_1_731".equals(item.path("productGroupID").asText(""))) return false;
                    groups++;
                }
            } catch (RuntimeException e) { return false; }
        }
        var label = switch (field) { case "ANC 지원" -> "액티브 노이즈 캔슬링"; case "방수" -> "방진·방수 등급"; default -> ""; };
        var expected = switch (field) { case "ANC 지원" -> "하이브리드 적응형 ANC"; case "방수" -> "IP54 방진 및 생활 방수 (이어버드만 해당)"; default -> ""; };
        if (groups != 1 || label.isEmpty()) return false;
        var tables = doc.select(".table-wrapper");
        if (tables.size() != 1) return false;
        var rows = tables.first().select(".table-row").stream().filter(row -> row.select(".label").size() == 1
                && label.equals(row.selectFirst(".label").text())).toList();
        return rows.size() == 1 && rows.getFirst().select(".description").size() == 1
                && expected.equals(rows.getFirst().selectFirst(".description").text());
    }
}
