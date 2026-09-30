package com.decisionassistant.guidance;

import com.decisionassistant.decision.DecisionTypes.TemplateKey;
import java.util.*;
import java.util.regex.Pattern;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

public final class CompatibilityMatcher {
    private CompatibilityMatcher() {}
    public static RequirementCheck check(TemplateKey category, String requested, List<ProductFact> facts, OfficialOffer offer) {
        if (requested == null || !OfficialStorePolicy.verifiesCompatibility(offer)) return null;
        var name = switch (category) { case EARBUDS -> "호환성"; case MONITOR -> "연결 단자"; default -> "OS"; };
        var evidence = facts.stream().filter(f -> name.equals(f.name())).toList();
        var values = evidence.stream().map(f -> value(category, requested, f)).toList();
        var status = RequirementStatus.UNKNOWN;
        if (!values.isEmpty() && values.stream().noneMatch(Objects::isNull) && values.stream().distinct().count() == 1)
            status = values.getFirst() ? RequirementStatus.MEETS : RequirementStatus.BELOW;
        var reason = status != RequirementStatus.UNKNOWN ? null : evidence.isEmpty() ? UnknownReason.MISSING
                : values.stream().filter(Objects::nonNull).distinct().count() > 1 ? UnknownReason.CONFLICT : UnknownReason.AMBIGUOUS;
        return new RequirementCheck("compatibility", category == TemplateKey.EARBUDS ? "기본 연결·사용 호환성" : "필요한 호환성", requested, status, evidence, reason);
    }
    private static Boolean value(TemplateKey category, String requested, ProductFact fact) {
        if (fact.value() == null || fact.quote() == null || "DISPUTED".equals(fact.verificationStatus())) return null;
        var text = fact.quote();
        if (text.matches("(?is).*(?:미확인|알 수 없|unknown|앱|app\\b).*")) return null;
        if (category != TemplateKey.EARBUDS && category != TemplateKey.MONITOR) {
            if (text.matches("(?is).*(?:선택|옵션|또는|설치 가능|별도 설치|optional|depending|\\bor\\b).*")) return null;
            if (platforms(fact.value()).size() > 1) return null;
        }
        var token = switch (requested) { case "멀티포인트" -> "(?:멀티포인트|multipoint)";
            case "Android" -> "(?:Android|안드로이드)"; default -> Pattern.quote(requested); };
        var positive = false; var negative = false;
        for (var clause : text.split("[,;\\n]|(?<=지원)\\s+(?=(?:Android|iOS|Windows|macOS))")) {
            if (!Pattern.compile("(?i)(?<![a-z])" + token + "(?![a-z])").matcher(clause).find()) continue;
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
    private static List<String> platforms(String value) {
        return List.of("Windows", "macOS", "Android", "iOS", "iPadOS").stream()
                .filter(p -> Pattern.compile("(?i)(?<![a-z])" + Pattern.quote(p) + "(?![a-z])").matcher(value).find()).toList();
    }
}
