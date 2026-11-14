package com.decisionassistant.guidance;

import com.decisionassistant.decision.DecisionTypes.TemplateKey;
import java.util.*;
import java.util.regex.Pattern;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

public final class CompatibilityMatcher {
    private CompatibilityMatcher() {}
    public static RequirementCheck check(TemplateKey category, String requested, List<ProductFact> facts, OfficialOffer offer) {
        return check(category, requested, facts, offer, false);
    }
    public static RequirementCheck check(TemplateKey category, String requested, List<ProductFact> facts, OfficialOffer offer, boolean catalog) {
        if (requested == null || !catalog && !OfficialStorePolicy.verifiesCompatibility(offer)) return null;
        var name = switch (category) { case EARBUDS -> "호환성"; case MONITOR -> "연결 단자"; default -> "OS"; };
        var evidence = facts.stream().filter(f -> name.equals(f.name()))
                .filter(f -> category != TemplateKey.EARBUDS || relevantTo(requested, f)).toList();
        var values = evidence.stream().map(f -> value(category, requested, f, catalog)).toList();
        var status = RequirementStatus.UNKNOWN;
        if (!values.isEmpty() && values.stream().noneMatch(Objects::isNull) && values.stream().distinct().count() == 1)
            status = values.getFirst() ? RequirementStatus.MEETS : RequirementStatus.BELOW;
        var reason = status != RequirementStatus.UNKNOWN ? null : evidence.isEmpty() ? UnknownReason.MISSING
                : values.stream().filter(Objects::nonNull).distinct().count() > 1 ? UnknownReason.CONFLICT : UnknownReason.AMBIGUOUS;
        return new RequirementCheck("compatibility", category == TemplateKey.EARBUDS ? "기본 연결·사용 호환성" : "필요한 호환성", requested, status, evidence, reason);
    }
    private static Boolean value(TemplateKey category, String requested, ProductFact fact, boolean catalog) {
        if (fact.value() == null || fact.quote() == null || "DISPUTED".equals(fact.verificationStatus())) return null;
        if (catalog && category == TemplateKey.EARBUDS && SamsungBuds4Verifier.basicPlatform(fact, requested)) return true;
        if (catalog && category == TemplateKey.EARBUDS && AppleSpecificationVerifier.basicIos(fact, requested)) return true;
        var text = fact.quote();
        if (text.matches("(?is).*(?:미확인|알 수 없|unknown).*")) return null;
        if (category == TemplateKey.EARBUDS) {
            // 쉼표·줄바꿈으로 이어지는 앱 플랫폼 목록 전체를 제외한다. 명시적으로 분리된 구간만 독립 판정한다.
            text = Arrays.stream(text.split(";"))
                    .filter(segment -> !segment.matches("(?is).*(?:앱|\\bapp\\b).*"))
                    .collect(java.util.stream.Collectors.joining(";"));
        } else if (text.matches("(?is).*(?:앱|\\bapp\\b).*")) return null;
        if (category != TemplateKey.EARBUDS && category != TemplateKey.MONITOR) {
            if (text.matches("(?is).*(?:선택|옵션|또는|설치 가능|별도 설치|optional|depending|\\bor\\b).*")) return null;
            if (platforms(fact.value()).size() > 1) return null;
        }
        var positive = false; var negative = false;
        for (var clause : text.split("[,;\\n]|(?<=지원)\\s+(?=(?:Android|iOS|Windows|macOS))")) {
            if (!targetPattern(requested).matcher(clause).find()) continue;
            if (category == TemplateKey.EARBUDS && "멀티포인트".equals(requested)
                    && clause.strip().matches("(?i)Multipoint Connection\\s*:?\\s*(?:Yes|No)")) {
                positive |= clause.strip().toLowerCase(Locale.ROOT).endsWith("yes");
                negative |= clause.strip().toLowerCase(Locale.ROOT).endsWith("no");
                continue;
            }
            if (clause.matches("(?is).*(?:미지원|지원하지 않|지원 안|불가|없음|not supported|unsupported|without).*")) { negative = true; continue; }
            if (category == TemplateKey.EARBUDS) {
                positive |= (clause.matches("(?is).*(?:호환|지원|연결|compatible|supported).*" ) || text.startsWith("호환성:"))
                        && !clause.matches("(?is).*(?:예정|계획|일부 기능|추가 기능).*" );
            } else if (category == TemplateKey.MONITOR) {
                positive |= clause.matches("(?is).*(?:영상|입력|displayport alt|dp alt|video|input).*" );
            } else positive = true;
        }
        if (positive && negative) return null;
        if (negative) return false;
        if (positive) return true;
        if (category != TemplateKey.EARBUDS && category != TemplateKey.MONITOR) {
            var found = platforms(fact.value());
            if (found.size() == 1 && !found.getFirst().equalsIgnoreCase(requested)) return false;
        }
        return null;
    }
    private static boolean relevantTo(String requested, ProductFact fact) {
        var text = Objects.toString(fact.value(), "") + " " + Objects.toString(fact.quote(), "");
        if (targetPattern(requested).matcher(text).find()) return true;
        // 다른 플랫폼의 명시적 근거는 선택한 플랫폼의 모호함·상충으로 합치지 않는다.
        // 대상이 명시되지 않은 일반적인 미확인 근거는 보존한다.
        return List.of("Android", "iOS", "멀티포인트").stream().noneMatch(target -> targetPattern(target).matcher(text).find());
    }
    private static Pattern targetPattern(String requested) {
        var token = switch (requested) { case "멀티포인트" -> "(?:멀티포인트|multipoint)";
            case "Android" -> "(?:Android|안드로이드)"; default -> Pattern.quote(requested); };
        return Pattern.compile("(?i)(?<![a-z])" + token + "(?![a-z])");
    }
    private static List<String> platforms(String value) {
        return List.of("Windows", "macOS", "Android", "iOS", "iPadOS").stream()
                .filter(p -> Pattern.compile("(?i)(?<![a-z])" + Pattern.quote(p) + "(?![a-z])").matcher(value).find()).toList();
    }
}
