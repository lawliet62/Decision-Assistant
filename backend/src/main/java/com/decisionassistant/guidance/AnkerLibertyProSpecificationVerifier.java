package com.decisionassistant.guidance;
import org.jsoup.Jsoup;

final class AnkerLibertyProSpecificationVerifier {
    static final String URL = "https://www.ankerjapan.com/products/d1203";
    static final String QUOTE = "IP55";
    private AnkerLibertyProSpecificationVerifier() {}
    static boolean matches(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, URL);
        var canonical = doc.select("link[rel=canonical]"); var headings = doc.select("h1");
        if (canonical.size() != 1 || !URL.equals(canonical.first().absUrl("href")) || headings.size() != 1
                || !"Soundcore Liberty 5 Pro".equals(headings.first().text())
                || !"Soundcore Liberty 5 Pro | ワイヤレスイヤホンの製品情報 | Anker Japan 公式オンラインストア".equals(doc.title())) return false;
        var specifications = doc.select(".pfs-text").stream().filter(e -> e.select("h3").size() == 1
                && "IP55 防塵・防水規格".equals(e.select("h3").first().text()) && e.select("p").size() == 1
                && e.select("p").first().text().contains("IP55の防塵・防水規格に対応")).toList();
        return specifications.size() == 1;
    }
}
