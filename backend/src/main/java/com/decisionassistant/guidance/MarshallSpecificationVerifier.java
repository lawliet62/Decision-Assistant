package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import tools.jackson.databind.json.JsonMapper;

/** 단일 등록 모델의 구조화 식별자와 사양 행을 함께 확인한다. */
final class MarshallSpecificationVerifier {
    private static final String URL = "https://www.marshall.com/kr/ko/product/motif-ii-anc?pid=1006450";
    private static final String RENDER_URL = "https://marshall-production-1.mobify-storefront.com/kr/ko/product/motif-ii-anc?pid=1006450";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private MarshallSpecificationVerifier() {}
    static boolean matches(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, URL);
        var canonical = doc.select("link[rel=canonical]");
        var titles = doc.select("h1");
        if (canonical.size() != 1 || !URL.equals(canonical.first().absUrl("href"))
                || titles.size() != 1 || !"MOTIF II A.N.C.".equals(titles.first().text())) return false;
        int products = 0;
        for (var script : doc.select("script[type=application/ld+json]")) {
            try {
                var item = JSON.readTree(script.data());
                if ("Product".equals(item.path("@type").asText(""))) {
                    if (!"1006450".equals(item.path("sku").asText("")) || !"MOTIF II A.N.C.".equals(item.path("name").asText(""))
                            || !(URL.equals(item.path("url").asText("")) || RENDER_URL.equals(item.path("url").asText("")))) return false;
                    products++;
                }
            } catch (RuntimeException ignored) { return false; }
        }
        var labels = doc.select("p").stream().filter(p -> "액티브 노이즈 캔슬링".equals(p.text())).toList();
        if (products != 1 || labels.size() != 1 || labels.getFirst().parent() == null) return false;
        var row = labels.getFirst().parent().select("p");
        return row.size() == 2 && "액티브 노이즈 캔슬링".equals(row.get(0).text()) && "예".equals(row.get(1).text());
    }
}
