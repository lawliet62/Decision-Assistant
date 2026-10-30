package com.decisionassistant.guidance;

import org.jsoup.Jsoup;

/** 제품 세대와 해당 사양 영역의 단일 항목을 대조한다. */
final class AppleSpecificationVerifier {
    private AppleSpecificationVerifier() {}
    static boolean matches(String html, String name) {
        return matches(html, name, false);
    }
    static boolean matchesAirpods5(String html, String name) {
        return matches(html, name, true);
    }
    private static boolean matches(String html, String name, boolean airpods5) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html);
        if (!doc.title().equals(airpods5 ? "AirPods 5 - 제품 사양 - Apple (KR)" : "AirPods Pro 3 - 제품 사양 - Apple (KR)")) return false;
        var selector = switch (name) {
            case "ANC 지원" -> ".section-audio";
            case "방수" -> ".section-water";
            default -> "";
        };
        if (selector.isEmpty()) return false;
        var sections = doc.select(".techspecs-section" + selector);
        if (sections.size() != 1) return false;
        var items = sections.first().select(".techspecs-list > li").stream()
                .map(e -> e.text().replace('\u00a0', ' ').replaceAll("\\s+", " ").strip()).toList();
        if (name.equals("ANC 지원")) {
            var matches = items.stream().filter(s -> s.contains("액티브 노이즈 캔슬링")).toList();
            return matches.size() == 1 && matches.getFirst().equals("액티브 노이즈 캔슬링");
        }
        var expected = airpods5 ? "먼지, 땀, 습기에 강한 방진 및 생활 방수 디자인(IP57)"
                : "먼지, 땀, 습기에 강한 방진 및 생활 방수 디자인(IP57): AirPods Pro 및 충전 케이스";
        return items.size() == 1 && items.getFirst().equals(expected);
    }
}
