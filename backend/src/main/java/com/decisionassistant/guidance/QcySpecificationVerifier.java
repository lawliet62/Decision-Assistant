package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import java.util.*;

/** 확인된 QCY 사양 표의 모델과 항목을 연결한다. 불확실하면 확인 시각을 갱신하지 않는다. */
final class QcySpecificationVerifier {
    private QcySpecificationVerifier() {}

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
