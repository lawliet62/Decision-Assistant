package com.decisionassistant.ai;

import com.decisionassistant.evaluation.DecisionSnapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class AiContracts {

    private AiContracts() {
    }

    public record StructureInput(
            String title,
            String intendedUse,
            String notes,
            List<OptionInput> options
    ) {
    }

    public record OptionInput(UUID id, String name, String description, String sourceText) {
    }

    public record StructureProposal(
            List<CriterionSuggestion> criteria,
            List<ValueSuggestion> values,
            List<ConstraintSuggestion> constraints,
            List<String> missingInformation,
            List<String> assumptions
    ) {
    }

    public record CriterionSuggestion(
            String name,
            String kind,
            String direction,
            BigDecimal suggestedWeight,
            BigDecimal worstAnchor,
            BigDecimal bestAnchor,
            String unit,
            String rationale
    ) {
    }

    public record ValueSuggestion(
            String optionId,
            String criterionName,
            BigDecimal numericValue,
            Integer rating,
            String certainty,
            String evidenceNote
    ) {
    }

    public record ConstraintSuggestion(
            String name,
            String kind,
            String criterionName,
            BigDecimal threshold,
            String rationale
    ) {
    }

    public record LlmAssessment(
            String recommendedOptionId,
            boolean abstained,
            String summary,
            List<String> decisiveFactors,
            List<String> tradeOffs,
            List<String> assumptions,
            List<String> risks
    ) {
    }

    public record AiResult<T>(
            T value,
            long inputTokens,
            long outputTokens
    ) {
    }

    public interface DecisionStructurer {
        AiResult<StructureProposal> structure(StructureInput input);
    }

    public interface DecisionAssessor {
        AiResult<LlmAssessment> assess(DecisionSnapshot snapshot);
    }
}
