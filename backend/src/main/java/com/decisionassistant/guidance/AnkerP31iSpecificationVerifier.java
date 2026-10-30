package com.decisionassistant.guidance;

import org.jsoup.Jsoup;

final class AnkerP31iSpecificationVerifier {
    static final String URL = "https://www.ankerjapan.com/products/d1202";
    static final String QUOTE = "アクティブノイズキャンセリング搭載";
    private AnkerP31iSpecificationVerifier() {}
    static boolean matches(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, URL);
        var canonical = doc.select("link[rel=canonical]"); var headings = doc.select("h1"); var descriptions = doc.select("meta[name=description]");
        if (canonical.size() != 1 || !URL.equals(canonical.first().absUrl("href")) || headings.size() != 1
                || !"Soundcore P31i".equals(headings.first().text()) || descriptions.size() != 1
                || !"Soundcore P31i | 完全ワイヤレスイヤホンの製品情報 | Anker Japan 公式オンラインストア".equals(doc.title())) return false;
        var description = descriptions.first().attr("content");
        return description.startsWith("Soundcore P31iならAnker (アンカー) Japan 公式オンラインストア。" + QUOTE + "。");
    }
}
