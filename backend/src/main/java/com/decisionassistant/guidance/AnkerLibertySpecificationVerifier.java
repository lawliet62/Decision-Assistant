package com.decisionassistant.guidance;
import org.jsoup.Jsoup;

final class AnkerLibertySpecificationVerifier {
    static final String URL = "https://www.soundcore.com/products/a3957-liberty-5-tws-earbuds";
    static final String QUOTE = "Real-Time Adaptive ANC 3.0";
    private AnkerLibertySpecificationVerifier() {}
    static boolean matches(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, URL);
        var canonical = doc.select("link[rel=canonical]"); var headings = doc.select("h1"); var descriptions = doc.select("meta[name=description]");
        if (canonical.size() != 1 || !URL.equals(canonical.first().absUrl("href")) || headings.size() != 1
                || !"soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby Audio".equals(headings.first().text())
                || descriptions.size() != 1 || !"soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby 3D Audio".equals(doc.title())) return false;
        var description = descriptions.first().attr("content");
        return description.startsWith("Buy soundcore Liberty 5 earbuds with Dolby 3D Audio, adaptive ANC,")
                && doc.select("strong").stream().anyMatch(e -> (QUOTE + ":").equals(e.text()));
    }
}
