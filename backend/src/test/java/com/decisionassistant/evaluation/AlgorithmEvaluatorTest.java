package com.decisionassistant.evaluation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.decisionassistant.decision.DecisionTypes.ConstraintKind.MANUAL;
import static com.decisionassistant.decision.DecisionTypes.ConstraintStatus.FAIL;
import static com.decisionassistant.decision.DecisionTypes.ConstraintStatus.PASS;
import static com.decisionassistant.decision.DecisionTypes.CriterionKind.NUMERIC_UTILITY;
import static com.decisionassistant.decision.DecisionTypes.CriterionKind.RATING_1_TO_5;
import static com.decisionassistant.decision.DecisionTypes.PreferenceDirection.LOWER_BETTER;
import static com.decisionassistant.decision.DecisionTypes.PreferenceDirection.NOT_APPLICABLE;
import static com.decisionassistant.decision.DecisionTypes.ValueCertainty.CONFIRMED;
import static com.decisionassistant.decision.DecisionTypes.ValueCertainty.ESTIMATED;
import static org.assertj.core.api.Assertions.assertThat;

class AlgorithmEvaluatorTest {

    private final AlgorithmEvaluator evaluator = new AlgorithmEvaluator();

    @Test
    void appliesConstraintsAnchorsWeightsAndEstimatedFlag() {
        var price = UUID.randomUUID();
        var fit = UUID.randomUUID();
        var optionA = UUID.randomUUID();
        var optionB = UUID.randomUUID();
        var constraint = UUID.randomUUID();
        var snapshot = new DecisionSnapshot(
                UUID.randomUUID(), 3, "Laptop", "Development", null,
                List.of(
                        new DecisionSnapshot.OptionSnapshot(optionA, "A", null, List.of(
                                new DecisionSnapshot.ValueSnapshot(price, BigDecimal.valueOf(1200), null, CONFIRMED, null, null),
                                new DecisionSnapshot.ValueSnapshot(fit, null, 4, ESTIMATED, null, null))),
                        new DecisionSnapshot.OptionSnapshot(optionB, "B", null, List.of(
                                new DecisionSnapshot.ValueSnapshot(price, BigDecimal.valueOf(1000), null, CONFIRMED, null, null),
                                new DecisionSnapshot.ValueSnapshot(fit, null, 5, CONFIRMED, null, null)))),
                List.of(
                        new DecisionSnapshot.CriterionSnapshot(price, "Price", NUMERIC_UTILITY, LOWER_BETTER,
                                BigDecimal.valueOf(2), BigDecimal.valueOf(2000), BigDecimal.valueOf(1000), "USD"),
                        new DecisionSnapshot.CriterionSnapshot(fit, "Fit", RATING_1_TO_5, NOT_APPLICABLE,
                                BigDecimal.ONE, null, null, null)),
                List.of(new DecisionSnapshot.ConstraintSnapshot(
                        constraint, "Compatibility", MANUAL, Map.of(optionA, PASS, optionB, FAIL))));

        var result = evaluator.evaluate(snapshot);

        assertThat(result.recommendedOptionId()).isEqualTo(optionA);
        assertThat(result.options()).hasSize(2);
        assertThat(result.options().getFirst().score()).isEqualByComparingTo("78.33");
        assertThat(result.options().getFirst().usesEstimatedValues()).isTrue();
        assertThat(result.options().getLast().feasible()).isFalse();
        assertThat(result.options().getLast().failedConstraints()).containsExactly("Compatibility");
    }

    @Test
    void clampsNumericUtilityOutsideAnchors() {
        var criterion = UUID.randomUUID();
        var low = UUID.randomUUID();
        var high = UUID.randomUUID();
        var snapshot = new DecisionSnapshot(
                UUID.randomUUID(), 0, "Test", null, null,
                List.of(
                        option(low, criterion, BigDecimal.valueOf(500)),
                        option(high, criterion, BigDecimal.valueOf(2500))),
                List.of(new DecisionSnapshot.CriterionSnapshot(
                        criterion, "Price", NUMERIC_UTILITY, LOWER_BETTER, BigDecimal.ONE,
                        BigDecimal.valueOf(2000), BigDecimal.valueOf(1000), "USD")),
                List.of());

        var result = evaluator.evaluate(snapshot);

        assertThat(result.options().getFirst().score()).isEqualByComparingTo("100.00");
        assertThat(result.options().getLast().score()).isEqualByComparingTo("0.00");
    }

    private DecisionSnapshot.OptionSnapshot option(UUID id, UUID criterion, BigDecimal value) {
        return new DecisionSnapshot.OptionSnapshot(id, id.toString(), null, List.of(
                new DecisionSnapshot.ValueSnapshot(criterion, value, null, CONFIRMED, null, null)));
    }
}
