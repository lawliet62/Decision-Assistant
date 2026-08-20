package com.decisionassistant.evaluation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static com.decisionassistant.decision.DecisionTypes.ConstraintStatus.FAIL;
import static com.decisionassistant.decision.DecisionTypes.CriterionKind.NUMERIC_UTILITY;
import static com.decisionassistant.decision.DecisionTypes.ValueCertainty.ESTIMATED;

@Component
public class AlgorithmEvaluator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public AlgorithmResult evaluate(DecisionSnapshot snapshot) {
        var totalWeight = snapshot.criteria().stream()
                .map(DecisionSnapshot.CriterionSnapshot::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var results = new ArrayList<AlgorithmResult.OptionResult>();
        for (var option : snapshot.options()) {
            var failed = snapshot.constraints().stream()
                    .filter(constraint -> constraint.optionStatuses().get(option.id()) == FAIL)
                    .map(DecisionSnapshot.ConstraintSnapshot::name)
                    .toList();
            var feasible = failed.isEmpty();
            var values = new HashMap<UUID, DecisionSnapshot.ValueSnapshot>();
            option.values().forEach(value -> values.put(value.criterionId(), value));
            var contributions = new ArrayList<AlgorithmResult.Contribution>();
            var score = BigDecimal.ZERO;
            var estimated = false;

            for (var criterion : snapshot.criteria()) {
                var value = values.get(criterion.id());
                estimated |= value.certainty() == ESTIMATED;
                var utility = utility(criterion, value);
                var points = utility
                        .multiply(criterion.weight())
                        .divide(totalWeight, 8, RoundingMode.HALF_UP)
                        .multiply(ONE_HUNDRED);
                contributions.add(new AlgorithmResult.Contribution(
                        criterion.id(), criterion.name(), rounded(utility), rounded(points)));
                score = score.add(points);
            }

            results.add(new AlgorithmResult.OptionResult(
                    option.id(), option.name(), feasible, rounded(score), estimated,
                    List.copyOf(contributions), failed));
        }

        var recommendation = results.stream()
                .filter(AlgorithmResult.OptionResult::feasible)
                .max(Comparator.comparing(AlgorithmResult.OptionResult::score))
                .map(AlgorithmResult.OptionResult::optionId)
                .orElse(null);
        return new AlgorithmResult(recommendation, List.copyOf(results));
    }

    private BigDecimal utility(DecisionSnapshot.CriterionSnapshot criterion,
                               DecisionSnapshot.ValueSnapshot value) {
        BigDecimal utility;
        if (criterion.kind() == NUMERIC_UTILITY) {
            utility = value.numericValue().subtract(criterion.worstAnchor())
                    .divide(criterion.bestAnchor().subtract(criterion.worstAnchor()), 8, RoundingMode.HALF_UP);
        } else {
            utility = BigDecimal.valueOf(value.rating() - 1L)
                    .divide(BigDecimal.valueOf(4), 8, RoundingMode.HALF_UP);
        }
        return utility.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private BigDecimal rounded(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
