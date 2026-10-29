package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import java.util.*;

/** 확인된 QCY 사양 표의 모델과 항목을 연결한다. 불확실하면 확인 시각을 갱신하지 않는다. */
final class QcySpecificationVerifier {
    private QcySpecificationVerifier() {}

    static boolean matchesHt16Anc(String html) {
        return matchesStoreProduct(html, "990", "QCY HT16 멜로버즈 무선 블루투스 이어폰 ANC 노이즈캔슬링 블랙");
    }
    static boolean matchesT17AppForm(String html) {
        return matchesStoreProduct(html, "563", "QCY T17APP 블루투스이어폰 커널형 무선이어폰 저지연 큐씨와이 초경량 블루투스5.3버전 블랙");
    }
    private static boolean matchesStoreProduct(String html, String id, String name) {
        if (html == null || html.length() > 2_000_000) return false;
        var url = "https://qcystore.co.kr/product/detail.html?product_no=" + id;
        var doc = Jsoup.parse(html, url);
        var canonical = doc.select("link[rel=canonical]");
        var headings = doc.select("h1");
        if (canonical.size() != 1 || !OfficialProductParser.samePage(url, canonical.first().absUrl("href"))
                || !doc.title().equals(name + " - QCY스토어") || headings.size() != 1 || !headings.first().text().equals(name)) return false;
        int matches = 0;
        for (var script : doc.select("script[type=application/ld+json]")) {
            try {
                var item = tools.jackson.databind.json.JsonMapper.builder().build().readTree(script.data());
                if ("ProductGroup".equals(item.path("@type").asText(""))) {
                    if (!name.equals(item.path("name").asText("")) || !("cafe24_ylintlshop_1_" + id).equals(item.path("productGroupID").asText(""))) return false;
                    matches++;
                }
            } catch (RuntimeException ignored) { return false; }
        }
        return matches == 1;
    }

    static boolean matchesHt19WaterResistance(String html) {
        return matchesHt19ModelAndAnc(html) && matches(html, "QCY MeloBuds N60", "Water Resistance Range", "IPX5");
    }

    static boolean matchesHt19ModelAndAnc(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html);
        var subtitles = doc.select("p.product__text.subtitle");
        var models = doc.select(".specification-content").stream().filter(section -> rows(section).stream()
                .anyMatch(row -> row.equals("Product Name: QCY MeloBuds N60"))).toList();
        return doc.title().equals("QCY MeloBuds N60: Affordable Wireless Earbuds with Rich Sound – QCY Official")
                && subtitles.size() == 1
                && subtitles.first().text().equals("Bluetooth 6.0, 56dB ANC, Hi-Res, 45-Hour Playtime, HT19")
                && models.size() == 1;
    }

    static boolean matchesAnc(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html);
        if (!doc.title().equals("QCY T13 Pro – QCY Official")) return false;
        var models = doc.select(".specification-content").stream().filter(section -> rows(section).stream()
                .anyMatch(row -> row.equals("Product Name: QCY T13 Pro"))).toList();
        var descriptions = doc.select("meta[name=description]");
        if (models.size() != 1 || descriptions.size() != 1) return false;
        var description = descriptions.first().attr("content").replaceAll("\\s+", " ").strip();
        return description.startsWith("50dB Adaptive ANC ")
                && !description.matches("(?is).*(?:no\\s+anc|without\\s+anc|anc\\s+(?:not supported|unsupported)).*");
    }

    static boolean matches(String html, String model, String label, String value) {
        if (html == null || model == null || label == null || value == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html);
        var sections = doc.select(".specification-content");
        var modelSections = sections.stream().filter(section -> rows(section).stream()
                .anyMatch(row -> row.equals("Product Name: " + model))).toList();
        if (modelSections.size() != 1) return false;
        var prefix = label + ":";
        var entries = rows(modelSections.getFirst()).stream().filter(row -> row.startsWith(prefix)).toList();
        return entries.size() == 1 && entries.getFirst().equals(prefix + " " + value);
    }

    private static List<String> rows(org.jsoup.nodes.Element section) {
        var rows = new ArrayList<String>();
        for (var p : section.select(".spec-details p")) {
            var copy = p.clone();
            copy.select("br").before("\n").remove();
            for (var row : copy.wholeText().split("\\R")) {
                var normalized = row.replaceAll("\\s+", " ").strip();
                if (!normalized.isEmpty()) rows.add(normalized);
            }
        }
        return rows;
    }
}
