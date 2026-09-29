package com.decisionassistant.guidance;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

final class ProductEvidence {
    private ProductEvidence() {}
    static List<EvidenceBlock> extract(Document doc, JsonNode product, String url, String name, String identity) {
        var rows = new LinkedHashMap<String, EvidenceBlock>();
        var properties = product.path("additionalProperty");
        var nodes = new ArrayList<JsonNode>();
        if (properties.isArray()) properties.forEach(nodes::add); else if (properties.isObject()) nodes.add(properties);
        for (var property : nodes) {
            var label = property.path("name").asText("");
            var unit = property.path("unitText").asText("");
            add(rows, identity, label + (unit.isBlank() ? "" : " (" + unit + ")"), property.path("value").asText(""));
        }
        var roots = new ArrayList<>(ProductPageRegions.products(doc, name, url, product.path("sku").asText(product.path("productGroupID").asText(""))));
        if (ProductPageRegions.current(doc, url, name)) {
            var selector = switch (OfficialStorePolicy.store(url).orElseThrow().id()) {
                case "lg-kr" -> "#pdp-pc-specs-more, #pdp-mobile-specs-more";
                case "qcy-kr" -> ".xans-product-detail .infoArea, #prdDetail";
                default -> "";
            };
            if (!selector.isEmpty()) roots.addAll(doc.select(selector));
        }
        for (var root : roots) {
            for (var row : root.select("tr")) {
                if (!ProductPageRegions.owned(row, root)) continue;
                var label = row.children().stream().filter(e -> e.tagName().equals("th")).findFirst().orElse(null);
                var value = row.children().stream().filter(e -> e.tagName().equals("td")).findFirst().orElse(null);
                if (label != null && value != null) add(rows, identity, label.text(), value.text());
            }
            for (var label : root.select("dt")) {
                var value = label.nextElementSibling();
                if (value != null && value.tagName().equals("dd") && ProductPageRegions.owned(label, root))
                    add(rows, identity, label.text(), value.text());
            }
        }
        return List.copyOf(rows.values());
    }
    private static void add(Map<String, EvidenceBlock> rows, String identity, String label, String value) {
        label = label.strip(); value = value.strip();
        var name = factName(label);
        if (name == null || value.isEmpty() || label.length() > 120 || value.length() > 300 || rows.size() >= 60) return;
        var quote = label + ": " + value;
        rows.putIfAbsent(quote, new EvidenceBlock("b" + rows.size(), identity, name, label, value, quote));
    }
    static String factName(String label) {
        var key = label.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("\\s*\\([^)]*\\)", "").strip();
        return switch (key) {
            case "메모리", "메모리 용량", "ram", "memory", "memory capacity" -> "메모리";
            case "ssd", "저장공간", "저장 공간", "스토리지", "storage", "storage capacity" -> "저장공간";
            case "운영체제", "운영 체제", "os", "operating system" -> "OS";
            case "주사율", "화면 주사율", "refresh rate" -> "주사율";
            case "해상도", "resolution" -> "해상도";
            case "anc", "anc 지원", "액티브 노이즈 캔슬링" -> "ANC 지원";
            case "전용 펜", "전용 펜 지원", "펜 지원" -> "펜 지원";
            case "호환성", "기본 호환성", "지원 기기", "compatible devices", "compatibility", "멀티포인트", "multipoint" -> "호환성";
            case "입력 단자", "영상 입력", "연결 단자", "input ports" -> "연결 단자";
            case "전용 앱", "앱 지원", "app compatibility" -> "전용 앱";
            case "기기별 추가 기능", "기기별 전용 기능" -> "기기별 추가 기능";
            case "방수", "방수 등급" -> "방수";
            default -> null;
        };
    }
}
