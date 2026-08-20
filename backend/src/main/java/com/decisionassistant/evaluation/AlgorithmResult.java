package com.decisionassistant.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AlgorithmResult(
        UUID recommendedOptionId,
        List<OptionResult> options
) {
    public record OptionResult(
            UUID optionId,
            String optionName,
            boolean feasible,
            BigDecimal score,
            boolean usesEstimatedValues,
            List<Contribution> contributions,
            List<String> failedConstraints
    ) {
    }

    public record Contribution(
            UUID criterionId,
            String criterionName,
            BigDecimal utility,
            BigDecimal weightedPoints
    ) {
    }
}
