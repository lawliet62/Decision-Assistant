package com.decisionassistant.evaluation;

import com.decisionassistant.decision.DecisionTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DecisionSnapshot(
        UUID decisionId,
        long decisionVersion,
        String title,
        String intendedUse,
        String notes,
        List<OptionSnapshot> options,
        List<CriterionSnapshot> criteria,
        List<ConstraintSnapshot> constraints
) {
    public record OptionSnapshot(
            UUID id,
            String name,
            String description,
            List<ValueSnapshot> values
    ) {
    }

    public record CriterionSnapshot(
            UUID id,
            String name,
            DecisionTypes.CriterionKind kind,
            DecisionTypes.PreferenceDirection direction,
            BigDecimal weight,
            BigDecimal worstAnchor,
            BigDecimal bestAnchor,
            String unit
    ) {
    }

    public record ValueSnapshot(
            UUID criterionId,
            BigDecimal numericValue,
            Integer rating,
            DecisionTypes.ValueCertainty certainty,
            String sourceUrl,
            String sourceNote
    ) {
    }

    public record ConstraintSnapshot(
            UUID id,
            String name,
            DecisionTypes.ConstraintKind kind,
            Map<UUID, DecisionTypes.ConstraintStatus> optionStatuses
    ) {
    }
}
