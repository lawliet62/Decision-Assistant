package com.decisionassistant.guidance;
import org.jsoup.Jsoup;
import tools.jackson.databind.json.JsonMapper;

final class AnkerAeroclipSpecificationVerifier {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private AnkerAeroclipSpecificationVerifier() {}
    static boolean matches(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, AnkerProductVerifier.AERO_URL);
        var canonical = doc.select("link[rel=canonical]"); var headings = doc.select("h1");
        if (canonical.size() != 1 || !OfficialProductParser.samePage(AnkerProductVerifier.AERO_URL, canonical.first().absUrl("href"))
                || headings.size() != 1 || !AnkerProductVerifier.AERO_NAME.equals(headings.first().text())
                || !"에어로클립 오픈형 러닝 블루투스이어폰 A3388".equals(doc.title())) return false;
        int matches = 0;
        for (var script : doc.select("script[type=application/ld+json]")) {
            try {
                var item = JSON.readTree(script.data());
                if ("ProductGroup".equals(item.path("@type").asText(""))) {
                    if (!AnkerProductVerifier.AERO_NAME.equals(item.path("name").asText(""))
                            || !"cafe24_ankerkr_1_45".equals(item.path("productGroupID").asText(""))) return false;
                    matches++;
                }
            } catch (RuntimeException ignored) { return false; }
        }
        return matches == 1;
    }
}
