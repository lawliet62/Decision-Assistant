package com.decisionassistant.evaluation;

import com.decisionassistant.common.InvalidDecisionException;
import com.decisionassistant.decision.ConstraintResult;
import com.decisionassistant.decision.Decision;
import com.decisionassistant.decision.DecisionConstraint;
import com.decisionassistant.decision.DecisionTypes;
import com.decisionassistant.decision.OptionValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.decisionassistant.decision.DecisionTypes.ConstraintKind.NUMERIC_MAX;
import static com.decisionassistant.decision.DecisionTypes.ConstraintKind.NUMERIC_MIN;
import static com.decisionassistant.decision.DecisionTypes.ConstraintStatus.FAIL;
import static com.decisionassistant.decision.DecisionTypes.ConstraintStatus.PASS;
import static com.decisionassistant.decision.DecisionTypes.ConstraintStatus.UNKNOWN;
import static com.decisionassistant.decision.DecisionTypes.CriterionKind.NUMERIC_UTILITY;
import static com.decisionassistant.decision.DecisionTypes.PreferenceDirection.HIGHER_BETTER;
import static com.decisionassistant.decision.DecisionTypes.PreferenceDirection.LOWER_BETTER;

@Component
public class SnapshotFactory {

    public DecisionSnapshot create(Decision decision) {
        var violations = new ArrayList<String>();
        if (decision.options().size() < 2 || decision.options().size() > 5) {
            violations.add("Option은 2~5개여야 합니다.");
        }

        var criteria = decision.criteria().stream().filter(criterion -> criterion.active()).toList();
        if (criteria.isEmpty()) {
            violations.add("활성 Criterion이 하나 이상 필요합니다.");
        }
        criteria.forEach(criterion -> {
            if (criterion.weight() == null || criterion.weight().compareTo(BigDecimal.ZERO) <= 0) {
                violations.add("Criterion '%s'의 weight는 0보다 커야 합니다.".formatted(criterion.name()));
            }
            if (criterion.kind() == NUMERIC_UTILITY
                    && (criterion.worstAnchor() == null || criterion.bestAnchor() == null
                    || criterion.worstAnchor().compareTo(criterion.bestAnchor()) == 0)) {
                violations.add("Numeric Criterion '%s'에는 서로 다른 worst/best anchor가 필요합니다."
                        .formatted(criterion.name()));
            }
            if (criterion.kind() == NUMERIC_UTILITY && criterion.worstAnchor() != null
                    && criterion.bestAnchor() != null) {
                var order = criterion.bestAnchor().compareTo(criterion.worstAnchor());
                if ((criterion.direction() == HIGHER_BETTER && order < 0)
                        || (criterion.direction() == LOWER_BETTER && order > 0)) {
                    violations.add("Criterion '%s'의 worst/best anchor가 선호 방향과 반대입니다."
                            .formatted(criterion.name()));
                }
            }
        });

        var valueByKey = new HashMap<String, OptionValue>();
        decision.values().forEach(value -> valueByKey.put(key(value.optionId(), value.criterionId()), value));
        var optionSnapshots = decision.options().stream().map(option -> {
            var values = new ArrayList<DecisionSnapshot.ValueSnapshot>();
            criteria.forEach(criterion -> {
                var value = valueByKey.get(key(option.id(), criterion.id()));
                if (value == null) {
                    violations.add("Option '%s'의 Criterion '%s' 값이 필요합니다."
                            .formatted(option.name(), criterion.name()));
                    return;
                }
                if (criterion.kind() == NUMERIC_UTILITY && value.numericValue() == null) {
                    violations.add("Option '%s'의 '%s'에는 numericValue가 필요합니다."
                            .formatted(option.name(), criterion.name()));
                }
                if (criterion.kind() != NUMERIC_UTILITY
                        && (value.rating() == null || value.rating() < 1 || value.rating() > 5)) {
                    violations.add("Option '%s'의 '%s' rating은 1~5여야 합니다."
                            .formatted(option.name(), criterion.name()));
                }
                values.add(new DecisionSnapshot.ValueSnapshot(
                        criterion.id(), value.numericValue(), value.rating(), value.certainty(),
                        value.sourceUrl(), value.sourceNote()));
            });
            return new DecisionSnapshot.OptionSnapshot(option.id(), option.name(), option.description(), values);
        }).toList();

        var resultByKey = new HashMap<String, ConstraintResult>();
        decision.constraintResults().forEach(result ->
                resultByKey.put(key(result.optionId(), result.constraintId()), result));
        var constraintSnapshots = decision.constraints().stream()
                .filter(DecisionConstraint::active)
                .map(constraint -> buildConstraint(decision, constraint, valueByKey, resultByKey, violations))
                .toList();

        if (!violations.isEmpty()) {
            throw new InvalidDecisionException(violations);
        }

        var criterionSnapshots = criteria.stream().map(criterion -> new DecisionSnapshot.CriterionSnapshot(
                criterion.id(), criterion.name(), criterion.kind(), criterion.direction(), criterion.weight(),
                criterion.worstAnchor(), criterion.bestAnchor(), criterion.unit())).toList();
        return new DecisionSnapshot(
                decision.id(), decision.version(), decision.title(), decision.context().intendedUse(),
                decision.context().notes(), optionSnapshots, criterionSnapshots, constraintSnapshots);
    }

    private DecisionSnapshot.ConstraintSnapshot buildConstraint(
            Decision decision,
            DecisionConstraint constraint,
            Map<String, OptionValue> values,
            Map<String, ConstraintResult> results,
            List<String> violations) {
        Map<UUID, DecisionTypes.ConstraintStatus> statuses = new LinkedHashMap<>();
        var numeric = constraint.kind() == NUMERIC_MIN || constraint.kind() == NUMERIC_MAX;
        if (numeric && (constraint.criterionId() == null || constraint.threshold() == null)) {
            violations.add("Numeric Constraint '%s'에는 criterion과 threshold가 필요합니다."
                    .formatted(constraint.name()));
        }
        for (var option : decision.options()) {
            DecisionTypes.ConstraintStatus status;
            if (numeric && constraint.criterionId() != null && constraint.threshold() != null) {
                var value = values.get(key(option.id(), constraint.criterionId()));
                if (value == null || value.numericValue() == null) {
                    status = UNKNOWN;
                } else if (constraint.kind() == NUMERIC_MIN) {
                    status = value.numericValue().compareTo(constraint.threshold()) >= 0 ? PASS : FAIL;
                } else {
                    status = value.numericValue().compareTo(constraint.threshold()) <= 0 ? PASS : FAIL;
                }
            } else {
                var result = results.get(key(option.id(), constraint.id()));
                status = result == null ? UNKNOWN : result.status();
            }
            if (status == UNKNOWN) {
                violations.add("Option '%s'의 Constraint '%s' 상태를 확인해야 합니다."
                        .formatted(option.name(), constraint.name()));
            }
            statuses.put(option.id(), status);
        }
        return new DecisionSnapshot.ConstraintSnapshot(
                constraint.id(), constraint.name(), constraint.kind(), Map.copyOf(statuses));
    }

    private String key(UUID left, UUID right) {
        return left + ":" + right;
    }
}
