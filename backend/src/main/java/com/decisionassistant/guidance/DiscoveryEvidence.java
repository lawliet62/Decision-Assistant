package com.decisionassistant.guidance;

import java.util.*;
import java.util.regex.Pattern;

/** 페이지 예산을 늘리지 않고 사양 주변의 원문과 제목 경계를 보존한다. */
final class DiscoveryEvidence {
    private DiscoveryEvidence() {}
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s+.+$");
    private static final String BOUNDARY = "\n\n<!-- evidence boundary -->\n\n";
    private record Passage(int position, int score, String text) {}

    static String excerpt(String markdown, List<String> facts) {
        if (markdown.length() <= 6000) return markdown;
        var terms = new ArrayList<>(facts);
        terms.addAll(List.of("ANC", "노이즈", "지원", "GB", "TB", "Hz", "KRW", "₩", "원", "가격"));
        var passages = new ArrayList<Passage>();
        var sections = sections(markdown);
        var position = 0;
        for (var section : sections) {
            var lines = section.split("\n", -1);
            var heading = lines[0].startsWith("#") ? lines[0] : "";
            var offset = 0;
            for (var line : lines) {
                int score = (int) terms.stream().filter(term -> line.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT))).count();
                if (score > 0) {
                    var start = Math.max(0, offset - 180);
                    var end = Math.min(section.length(), offset + line.length() + 220);
                    var text = (start > 0 && !heading.isBlank() ? heading + "\n" : "") + section.substring(start, end);
                    if (text.length() > 1800) text = text.substring(0, 1800);
                    passages.add(new Passage(position + offset, score, text));
                }
                offset += line.length() + 1;
            }
            position += section.length();
        }
        var selected = new ArrayList<Passage>();
        int size = 0;
        for (var passage : passages.stream().sorted(Comparator.comparingInt(Passage::score).reversed().thenComparingInt(Passage::position)).toList()) {
            if (selected.stream().anyMatch(item -> item.text().contains(passage.text()))) continue;
            if (size + passage.text().length() + BOUNDARY.length() > 6000) continue;
            selected.add(passage); size += passage.text().length() + BOUNDARY.length();
        }
        if (selected.isEmpty()) return markdown.substring(0, 6000);
        return selected.stream().sorted(Comparator.comparingInt(Passage::position)).map(Passage::text)
                .collect(java.util.stream.Collectors.joining(BOUNDARY));
    }

    static boolean belongsToProduct(String excerpt, String name, String quote, Collection<String> otherNames) {
        if (quote.contains(name)) return true;
        for (var segment : excerpt.split(Pattern.quote(BOUNDARY))) for (var section : sections(segment)) {
            var heading = section.lines().findFirst().orElse("");
            if (heading.startsWith("#") && heading.contains(name) && section.contains(quote)
                    && otherNames.stream().noneMatch(other -> !other.equals(name) && heading.contains(other))) return true;
        }
        return false;
    }

    private static List<String> sections(String text) {
        var matcher = HEADING.matcher(text);
        var starts = new ArrayList<Integer>(); starts.add(0);
        while (matcher.find()) if (matcher.start() > 0) starts.add(matcher.start());
        var result = new ArrayList<String>();
        for (int i = 0; i < starts.size(); i++) result.add(text.substring(starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : text.length()));
        return result;
    }
}
