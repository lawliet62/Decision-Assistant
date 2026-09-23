package com.decisionassistant.guidance;

import com.decisionassistant.decision.Decision;
import com.decisionassistant.decision.DecisionTypes;
import java.time.Instant;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.ProductFact;

/** 화면 표시와 최소 조건 판정이 동일한 수치를 사용하도록 한 곳에서 우선순위를 정한다. */
public final class ProductFactResolver {
    private ProductFactResolver() {}

    public static List<ProductFact> resolve(Decision decision, UUID optionId, List<ProductFact> extracted, Instant fetchedAt) {
        var facts = new ArrayList<>(extracted);
        for (var criterion : decision.criteria()) {
            if (criterion.kind() != DecisionTypes.CriterionKind.NUMERIC_UTILITY) continue;
            var name = "스토리지".equals(criterion.name()) ? "저장공간" : criterion.name();
            var value = decision.values().stream().filter(item -> item.optionId().equals(optionId)
                    && item.criterionId().equals(criterion.id()) && item.numericValue() != null).findFirst().orElse(null);
            if (value == null) continue;
            if (value.verificationStatus() != DecisionTypes.VerificationStatus.CONFIRMED
                    && facts.stream().anyMatch(item -> name.equals(item.name()))) continue;
            facts.removeIf(item -> name.equals(item.name()));
            facts.add(new ProductFact(name, value.numericValue().stripTrailingZeros().toPlainString() + " "
                    + Objects.toString(criterion.unit(), ""), value.sourceUrl(), value.sourceNote(), fetchedAt,
                    value.verificationStatus().name()));
        }
        return List.copyOf(facts);
    }
}
