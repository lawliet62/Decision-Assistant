package com.decisionassistant.guidance;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

/** 확인한 항목-값의 기본 사양만 정규화한다. 인용은 확장·조건 문구까지 보존한다. */
final class SpecNormalizer {
    static final String VERSION = "basic-spec-v1";
    private static final Pattern CAPACITY = Pattern.compile("(?i)^([0-9]+(?:\\.[0-9]+)?)\\s*(GB|TB)(?:\\s*\\([^()]*\\))?(?:\\s*\\+\\s*확장 슬롯\\s*[0-9]+(?:\\s*\\([^()]*\\))?)?$");
    private static final Pattern CONDITIONAL = Pattern.compile("(?i)최대|최소|선택|옵션|또는|up\\s+to|optional|depending|\\bor\\b|[~/]");
    private SpecNormalizer() {}
    static ProductFact fact(EvidenceBlock block, String url, Instant fetchedAt) {
        var raw = block.value(); var value = raw; String normalized = null;
        if (Set.of("메모리", "저장공간").contains(block.factName()) && !CONDITIONAL.matcher(raw).find()) {
            var unit = Pattern.compile("(?i)\\((GB|TB)\\)").matcher(block.label());
            if (raw.matches("[0-9]+(?:\\.[0-9]+)?") && unit.find()) value = raw + " " + unit.group(1).toUpperCase(Locale.ROOT);
            var match = CAPACITY.matcher(value);
            if (match.matches()) { value = match.group(1) + " " + match.group(2).toUpperCase(Locale.ROOT); normalized = VERSION; }
            else value = raw;
        } else if (block.factName().equals("주사율") && !CONDITIONAL.matcher(raw).find()) {
            if (raw.matches("[0-9]+") && block.label().matches("(?i).*\\(Hz\\).*$")) { value = raw + " Hz"; normalized = VERSION; }
        } else if (Set.of("ANC 지원", "펜 지원").contains(block.factName())) {
            if (Set.of("지원", "지원함", "있음", "탑재", "supported", "yes").contains(raw.toLowerCase(Locale.ROOT))) {
                value = "지원"; normalized = VERSION;
            } else if (Set.of("미지원", "없음", "지원하지 않음", "not supported", "no").contains(raw.toLowerCase(Locale.ROOT))) {
                value = "미지원"; normalized = VERSION;
            }
        }
        return new ProductFact(block.factName(), value, url, block.quote(), fetchedAt, "UNREVIEWED", normalized);
    }
    static boolean fieldMatches(String name, String value, String quote) {
        if (name.equals("메모리") && quote.matches("(?is).*(?:그래픽|VRAM|video memory).*")) return false;
        if (name.equals("전용 앱")) return quote.matches("(?is).*(?:앱|\\bapp\\b).*" );
        if (name.equals("기기별 추가 기능")) return quote.matches("(?is).*(?:전용|추가 기능|exclusive|only).*" );
        if (name.equals("OS")) return quote.matches("(?is).*(?:운영\\s*체제|\\bOS\\b|operating system).*" ) && !quote.matches("(?is).*(?:앱|\\bapp\\b).*" );
        String field = switch (name) {
            case "메모리" -> "(?:메모리|(?<![a-zA-Z])RAM|memory)";
            case "저장공간" -> "(?:저장\\s*공간|스토리지|SSD|storage)";
            case "주사율" -> "(?:주사율|refresh rate)";
            case "해상도" -> "(?:해상도|resolution)";
            default -> null;
        };
        if (field == null) return true;
        var literal = Pattern.quote(value);
        // 'SSD 512GB RAM 8GB'에서 앞 사양의 512GB를 RAM 값으로 연결하지 않는다.
        return Pattern.compile("(?i)(?:" + field + "\\s*[:：]?\\s*" + literal + "|(?<![0-9.])" + literal
                + "\\s*" + field + "(?![a-z]|\\s*[:：]?\\s*[0-9]))").matcher(quote).find();
    }
}
