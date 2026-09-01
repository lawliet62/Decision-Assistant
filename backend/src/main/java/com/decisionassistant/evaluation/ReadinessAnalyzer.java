package com.decisionassistant.evaluation;

import com.decisionassistant.decision.Decision;
import com.decisionassistant.decision.DecisionTypes;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Component
public class ReadinessAnalyzer {

    public record OptionRange(UUID optionId, String optionName, BigDecimal lowerScore, BigDecimal upperScore) {}
    public record Result(String readiness, String summary, int missingValues, List<OptionRange> ranges) {}

    public Result analyze(Decision decision) {
        var active = decision.criteria().stream().filter(item -> item.active() && item.weight() != null
                && item.weight().compareTo(BigDecimal.ZERO) > 0).toList();
        var totalWeight = active.stream().map(item -> item.weight()).reduce(BigDecimal.ZERO, BigDecimal::add);
        var byKey = new HashMap<String, com.decisionassistant.decision.OptionValue>();
        decision.values().forEach(value -> byKey.put(key(value.optionId(), value.criterionId()), value));
        var ranges = new ArrayList<OptionRange>();
        var missing = 0;

        for (var option : decision.options()) {
            var lower = BigDecimal.ZERO;
            var upper = BigDecimal.ZERO;
            for (var criterion : active) {
                var share = totalWeight.signum() == 0 ? BigDecimal.ZERO
                        : criterion.weight().divide(totalWeight, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                var value = byKey.get(key(option.id(), criterion.id()));
                var utility = utility(criterion, value);
                if (utility == null) {
                    missing++;
                    upper = upper.add(share);
                } else {
                    var points = utility.multiply(share);
                    lower = lower.add(points);
                    upper = upper.add(points);
                }
            }
            ranges.add(new OptionRange(option.id(), option.name(), rounded(lower), rounded(upper)));
        }
        ranges.sort(Comparator.comparing(OptionRange::upperScore).reversed());
        var ready = decision.options().size() >= 2 && !active.isEmpty() && missing == 0;
        var summary = ready
                ? "비교 입력이 준비됐습니다. 검증된 점수 평가를 실행할 수 있습니다."
                : provisionalSummary(decision, missing, ranges);
        return new Result(ready ? "VERIFIED_READY" : "PROVISIONAL", summary, missing, List.copyOf(ranges));
    }

    private String provisionalSummary(Decision decision, int missing, List<OptionRange> ranges) {
        if (decision.options().isEmpty()) return "후보 링크를 추가하면 비교 초안을 만들 수 있습니다.";
        if (decision.options().size() == 1) return "첫 후보를 정리했습니다. 비교할 후보를 하나 더 추가하거나 대안 탐색을 사용하세요.";
        if (ranges.isEmpty()) return "우선순위와 후보 정보를 확인하면 잠정 비교를 시작할 수 있습니다.";
        var leader = ranges.getFirst();
        return "%s의 가능한 점수 범위가 현재 가장 높습니다. 미확인 값 %d개가 있어 결론은 잠정적입니다."
                .formatted(leader.optionName(), missing);
    }

    private BigDecimal utility(com.decisionassistant.decision.Criterion criterion,
                               com.decisionassistant.decision.OptionValue value) {
        if (value == null) return null;
        if (criterion.kind() == DecisionTypes.CriterionKind.RATING_1_TO_5) {
            if (value.rating() == null) return null;
            return BigDecimal.valueOf(value.rating() - 1L).divide(BigDecimal.valueOf(4), 8, RoundingMode.HALF_UP);
        }
        if (value.numericValue() == null || criterion.worstAnchor() == null || criterion.bestAnchor() == null
                || criterion.bestAnchor().compareTo(criterion.worstAnchor()) == 0) return null;
        return value.numericValue().subtract(criterion.worstAnchor())
                .divide(criterion.bestAnchor().subtract(criterion.worstAnchor()), 8, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private BigDecimal rounded(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private String key(UUID optionId, UUID criterionId) { return optionId + ":" + criterionId; }
}
