package com.decisionassistant.template;

import com.decisionassistant.decision.DecisionTypes.TemplateKey;
import java.util.List;

/** 최소 조건의 선택지와 비교 의미. 평가용 Criterion/Constraint와 독립적이다. */
public final class MinimumRequirements {
    private MinimumRequirements() {}
    public enum Rule { CAPACITY, REFRESH_RATE, RESOLUTION, SUPPORT }
    public record RequirementChoice(String id, String label, int minimum, int width, int height) {}
    public record MinimumRequirement(String id, String label, String factName, Rule rule,
                                     List<RequirementChoice> choices) {}

    public static List<MinimumRequirement> forCategory(TemplateKey key) {
        var storage = numeric("storage", "저장 공간", "저장공간", Rule.CAPACITY,
                key == TemplateKey.LAPTOP ? List.of(256, 512, 1000) : List.of(128, 256, 512));
        var refresh = numeric("refreshRate", "화면 주사율", "주사율", Rule.REFRESH_RATE,
                key == TemplateKey.MONITOR ? List.of(60, 120, 144, 165) : List.of(60, 90, 120));
        return switch (key) {
            case LAPTOP -> List.of(numeric("memory", "메모리", "메모리", Rule.CAPACITY, List.of(8, 16, 32)), storage);
            case PHONE -> List.of(storage, refresh);
            case TABLET -> List.of(storage, refresh, support("pen", "전용 펜 지원", "펜 지원"));
            case MONITOR -> List.of(new MinimumRequirement("resolution", "해상도", "해상도", Rule.RESOLUTION, List.of(
                    new RequirementChoice("fhd", "FHD 이상", 0, 1920, 1080),
                    new RequirementChoice("qhd", "QHD 이상", 0, 2560, 1440),
                    new RequirementChoice("uhd", "4K UHD 이상", 0, 3840, 2160))), refresh);
            case EARBUDS -> List.of(support("anc", "ANC 지원", "ANC 지원"));
            case CUSTOM -> List.of();
        };
    }

    private static MinimumRequirement numeric(String id, String label, String fact, Rule rule, List<Integer> values) {
        return new MinimumRequirement(id, label, fact, rule, values.stream().map(value -> new RequirementChoice(
                value.toString(), (rule == Rule.REFRESH_RATE ? value + "Hz" : value == 1000 ? "1TB" : value + "GB") + " 이상",
                value, 0, 0)).toList());
    }

    private static MinimumRequirement support(String id, String label, String fact) {
        return new MinimumRequirement(id, label, fact, Rule.SUPPORT,
                List.of(new RequirementChoice("required", "지원 필수", 0, 0, 0)));
    }
}
