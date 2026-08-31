package com.decisionassistant.evaluation;

import com.decisionassistant.common.InvalidDecisionException;
import com.decisionassistant.decision.Decision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.decisionassistant.decision.DecisionTypes.CriterionKind.RATING_1_TO_5;
import static com.decisionassistant.decision.DecisionTypes.PreferenceDirection.NOT_APPLICABLE;
import static com.decisionassistant.decision.DecisionTypes.TemplateKey;
import static com.decisionassistant.decision.DecisionTypes.ValueCertainty.CONFIRMED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnapshotFactoryTest {

    private final SnapshotFactory factory = new SnapshotFactory();

    @Test
    void blocksEvaluationWhenAnActiveValueIsMissing() {
        var decision = new Decision(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Laptop", TemplateKey.CUSTOM, "custom-v1", Instant.EPOCH);
        var optionA = decision.addOption(null, "A", null, null, null);
        decision.addOption(null, "B", null, null, null);
        var criterion = decision.addCriterion(null, "Fit", RATING_1_TO_5, NOT_APPLICABLE,
                BigDecimal.ONE, null, null, null, true);
        decision.addValue(null, optionA.id(), criterion.id(), null, 4, CONFIRMED, null, null);

        assertThatThrownBy(() -> factory.create(decision))
                .isInstanceOf(InvalidDecisionException.class)
                .hasMessageContaining("not ready");
    }
}
