package com.decisionassistant.decision;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DecisionDtos {

    private DecisionDtos() {
    }

    public record CreateDecisionRequest(
            @Size(max = 200) String title,
            DecisionTypes.TemplateKey templateKey,
            @Size(max = 4000) String intentText,
            @Size(max = 5) List<@Size(max = 2048) String> sourceUrls,
            Boolean aiConsent,
            Boolean webConsent,
            Boolean sensitiveDataAcknowledged
    ) {
    }

    public record DecisionSummary(
            UUID id,
            long version,
            String title,
            DecisionTypes.TemplateKey templateKey,
            DecisionTypes.DecisionStatus status,
            Instant updatedAt
    ) {
    }

    public record DecisionResponse(
            UUID id,
            long version,
            String title,
            DecisionTypes.TemplateKey templateKey,
            String templateVersion,
            DecisionTypes.DecisionStatus status,
            ContextData context,
            List<OptionData> options,
            List<CriterionData> criteria,
            List<OptionValueData> values,
            List<ConstraintData> constraints,
            List<ConstraintResultData> constraintResults,
            List<ResolutionData> resolutions,
            List<OutcomeData> outcomes,
            GuidanceData guidance,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record UpdateDraftRequest(
            long expectedVersion,
            @NotBlank @Size(max = 200) String title,
            @NotNull @Valid ContextData context,
            @NotNull @Size(max = 5) List<@Valid OptionData> options,
            @NotNull List<@Valid CriterionData> criteria,
            @NotNull List<@Valid OptionValueData> values,
            @NotNull List<@Valid ConstraintData> constraints,
            @NotNull List<@Valid ConstraintResultData> constraintResults
    ) {
    }

    public record ContextData(
            @Size(max = 1000) String intendedUse,
            BigDecimal budget,
            @Size(max = 3) String currency,
            String notes
    ) {
    }

    public record OptionData(
            UUID id,
            @NotBlank @Size(max = 200) String name,
            String description,
            @Size(max = 2048) String sourceUrl,
            String sourceText
    ) {
    }

    public record CriterionData(
            UUID id,
            @NotBlank @Size(max = 200) String name,
            @NotNull DecisionTypes.CriterionKind kind,
            @NotNull DecisionTypes.PreferenceDirection direction,
            @NotNull BigDecimal weight,
            BigDecimal worstAnchor,
            BigDecimal bestAnchor,
            @Size(max = 30) String unit,
            boolean active
    ) {
    }

    public record OptionValueData(
            UUID id,
            @NotNull UUID optionId,
            @NotNull UUID criterionId,
            BigDecimal numericValue,
            @Min(1) @Max(5) Integer rating,
            @NotNull DecisionTypes.ValueCertainty certainty,
            @Size(max = 2048) String sourceUrl,
            String sourceNote,
            DecisionTypes.ValueOrigin origin,
            DecisionTypes.VerificationStatus verificationStatus
    ) {
    }

    public record ConstraintData(
            UUID id,
            @NotBlank @Size(max = 200) String name,
            @NotNull DecisionTypes.ConstraintKind kind,
            UUID criterionId,
            BigDecimal threshold,
            @Size(max = 30) String unit,
            String description,
            boolean active
    ) {
    }

    public record ConstraintResultData(
            UUID id,
            @NotNull UUID optionId,
            @NotNull UUID constraintId,
            @NotNull DecisionTypes.ConstraintStatus status,
            String note
    ) {
    }

    public record CreateResolutionRequest(
            @NotNull DecisionTypes.ResolutionKind kind,
            UUID selectedOptionId,
            String rationale
    ) {
    }

    public record ResolutionData(
            UUID id,
            DecisionTypes.ResolutionKind kind,
            UUID selectedOptionId,
            String rationale,
            Instant createdAt
    ) {
    }

    public record CreateOutcomeRequest(
            @Min(1) @Max(5) int satisfaction,
            @NotNull DecisionTypes.ExpectationStatus expectationStatus,
            String notes,
            Boolean wouldChooseAgain,
            String workedWell,
            String disappointedBy
    ) {
    }

    public record OutcomeData(
            UUID id,
            int satisfaction,
            DecisionTypes.ExpectationStatus expectationStatus,
            String notes,
            Boolean wouldChooseAgain,
            String workedWell,
            String disappointedBy,
            Instant createdAt
    ) {
    }

    public record GuidanceData(
            String intentText,
            boolean aiConsent,
            DecisionTypes.ProcessingStatus processingStatus,
            String readiness,
            String summary,
            List<QuestionData> questions,
            List<EvidenceData> evidence,
            List<CandidateSuggestionData> candidateSuggestions,
            List<PreferenceData> appliedPreferences,
            List<ReminderData> reminders
    ) {
    }

    public record QuestionData(
            UUID id, String kind, String targetKey, String prompt, String impact,
            List<String> options, String answer, String status
    ) {
    }

    public record EvidenceData(
            UUID id, UUID optionId, String url, String status, String title,
            String excerpt, String error, Instant fetchedAt, boolean stale
    ) {
    }

    public record CandidateSuggestionData(
            UUID id, String name, String url, String reason, String status
    ) {
    }

    public record PreferenceData(
            UUID id, DecisionTypes.TemplateKey templateKey, String criterionName, String statement,
            int strength, String status, String confidence, UUID sourceDecisionId
    ) {
    }

    public record ReminderData(UUID id, Instant dueAt, String status, String channel) {
    }

    public record AddSourceRequest(@NotBlank @Size(max = 2048) String url) {
    }

    public record AnswerQuestionRequest(@NotBlank @Size(max = 2000) String answer) {
    }

    public record UpdateSuggestionRequest(@NotNull String action) {
    }

    public record ConfirmValueRequest(
            BigDecimal numericValue,
            @Min(1) @Max(5) Integer rating,
            Boolean disputed
    ) {
    }
}
