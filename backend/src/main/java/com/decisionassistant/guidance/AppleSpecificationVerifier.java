package com.decisionassistant.guidance;

import org.jsoup.Jsoup;

/** 제품 세대와 해당 사양 영역의 단일 항목을 대조한다. */
final class AppleSpecificationVerifier {
    static final String IOS_QUOTE = "최신 버전 iOS가 설치된 iPhone 모델";
    static final String BASIC_AUDIO = "AirPods은 이전 버전 소프트웨어를 사용하는 Apple 기기 및 Apple 기기가 아닌 제품과도 무선 Bluetooth 헤드폰으로 페어링해 사용할 수 있지만, 기능은 제한될 수 있습니다.";
    static boolean basicIos(DiscoveryContracts.ProductFact fact, String requested) {
        return "iOS".equals(requested) && "iOS".equals(fact.value()) && IOS_QUOTE.equals(fact.quote())
                && java.util.Set.of("https://www.apple.com/kr/airpods-5/specs/", "https://www.apple.com/kr/airpods-pro/specs/").contains(fact.sourceUrl());
    }
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
            case "호환성" -> ".section-requirements";
            default -> "";
        };
        if (selector.isEmpty()) return false;
        var sections = doc.select(".techspecs-section" + selector);
        if (sections.size() != 1) return false;
        var items = sections.first().select(".techspecs-list > li").stream()
                .map(e -> e.text().replace('\u00a0', ' ').replaceAll("\\s+", " ").strip()).toList();
        if (name.equals("호환성")) {
            var canonical = doc.select("link[rel=canonical]");
            var url = "https://www.apple.com/kr/" + (airpods5 ? "airpods-5" : "airpods-pro") + "/specs/";
            return doc.select("title").size() == 1 && canonical.size() == 1 && url.equals(canonical.first().attr("href"))
                    && java.util.Collections.frequency(items, IOS_QUOTE) == 1 && java.util.Collections.frequency(items, BASIC_AUDIO) == 1
                    && items.stream().filter(s -> s.contains("iOS")).count() == 1;
        }
        if (name.equals("ANC 지원")) {
            var matches = items.stream().filter(s -> s.contains("액티브 노이즈 캔슬링")).toList();
            return matches.size() == 1 && matches.getFirst().equals("액티브 노이즈 캔슬링");
        }
        var expected = airpods5 ? "먼지, 땀, 습기에 강한 방진 및 생활 방수 디자인(IP57)"
                : "먼지, 땀, 습기에 강한 방진 및 생활 방수 디자인(IP57): AirPods Pro 및 충전 케이스";
        return items.size() == 1 && items.getFirst().equals(expected);
    }
}
