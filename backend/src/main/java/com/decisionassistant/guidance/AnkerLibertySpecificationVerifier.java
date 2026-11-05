package com.decisionassistant.guidance;
import org.jsoup.Jsoup;

final class AnkerLibertySpecificationVerifier {
    static final String URL = "https://www.soundcore.com/products/a3957-liberty-5-tws-earbuds";
    static final String QUOTE = "Real-Time Adaptive ANC 3.0";
    static final String MULTIPOINT_QUOTE = "Multipoint Connection Yes";
    private AnkerLibertySpecificationVerifier() {}
    static boolean matches(String html) {
        var doc = productDocument(html);
        if (doc == null) return false;
        var description = doc.select("meta[name=description]").first().attr("content");
        return description.startsWith("Buy soundcore Liberty 5 earbuds with Dolby 3D Audio, adaptive ANC,")
                && doc.select("strong").stream().anyMatch(e -> (QUOTE + ":").equals(e.text()));
    }
    static boolean matchesMultipoint(String html) {
        var doc = productDocument(html);
        if (doc == null) return false;
        var tables = doc.select("table");
        if (tables.size() != 1) return false;
        var heading = tables.first().previousElementSibling();
        if (heading == null || !"h2".equals(heading.tagName()) || !"Specs".equals(heading.text())) return false;
        var rows = tables.first().select("tr").stream()
                .filter(row -> !row.children().isEmpty() && "Multipoint Connection".equals(row.child(0).text())).toList();
        return rows.size() == 1 && rows.getFirst().children().size() == 2
                && "td".equals(rows.getFirst().child(0).tagName()) && "td".equals(rows.getFirst().child(1).tagName())
                && "Yes".equals(rows.getFirst().child(1).text());
    }
    private static org.jsoup.nodes.Document productDocument(String html) {
        if (html == null || html.length() > 2_000_000) return null;
        var doc = Jsoup.parse(html, URL);
        var canonical = doc.select("link[rel=canonical]"); var headings = doc.select("h1"); var descriptions = doc.select("meta[name=description]");
        if (canonical.size() != 1 || !URL.equals(canonical.first().absUrl("href")) || headings.size() != 1
                || !"soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby Audio".equals(headings.first().text())
                || descriptions.size() != 1 || !"soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby 3D Audio".equals(doc.title())) return null;
        return doc;
    }
}
