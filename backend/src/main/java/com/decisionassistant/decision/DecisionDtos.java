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
            Boolean sensitiveDataAcknowledged,
            @Size(max = 5) List<@NotNull @Valid AddSourceRequest> selectedSources,
            @Valid com.decisionassistant.guidance.DiscoveryContracts.DiscoveryRequest discovery
    ) {
    }

    public record DiscoveryData(long version, String status, String error,
            com.decisionassistant.guidance.DiscoveryContracts.DiscoveryRequest conditions, Instant updatedAt,
            String policyVersion, String stage, UUID executionId, String emptyReason,
            int generation, int displayedGeneration, List<DiscoveryHistoryData> history) {}
    public record DiscoveryHistoryData(int generation, String status, String error, String emptyReason,
                                       String policyVersion, Instant completedAt, List<CandidateSuggestionData> candidates) {}
    public record RetryDiscoveryRequest(long expectedVersion) {}
    public record ProductFactsData(UUID optionId, List<com.decisionassistant.guidance.DiscoveryContracts.ProductFact> facts,
                                   Instant fetchedAt, String sourceExcerpt,
                                   List<com.decisionassistant.guidance.DiscoveryContracts.RequirementCheck> requirementChecks,
                                   com.decisionassistant.guidance.DiscoveryContracts.OfficialOffer officialOffer,
                                   com.decisionassistant.guidance.OfficialStorePolicy.SourceKind sourceKind,
                                   com.decisionassistant.guidance.DiscoveryContracts.RequirementCheck compatibilityCheck) {}

    @com.fasterxml.jackson.annotation.JsonPropertyOrder({"id", "version", "title", "templateKey", "status", "createdAt", "updatedAt", "phase", "result"})
    public record DecisionSummary(
            UUID id,
            long version,
            String title,
            DecisionTypes.TemplateKey templateKey,
            DecisionTypes.DecisionStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        public DecisionTypes.DecisionPhase getPhase() { return DecisionTypes.phase(status); }
        public DecisionTypes.DecisionResult getResult() { return DecisionTypes.result(status); }
    }

    public record DecisionPage(List<DecisionSummary> items, int page, int size, long totalElements, int totalPages) {}
    public record StatusCount(DecisionTypes.DecisionStatus status, long count) {}
    public enum ListSort { UPDATED_AT, CREATED_AT, TITLE, TEMPLATE_KEY }
    public enum ListDirection { ASC, DESC }

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
            Instant updatedAt,
            DiscoveryData discovery,
            List<ProductFactsData> productFacts,
            Comparison.Data comparison,
            DecisionTypes.DecisionPhase phase,
            DecisionTypes.DecisionResult result,
            DecisionTypes.EditMode editMode,
            UUID currentResolutionId,
            UUID revisionBaseId,
            boolean personalizationEnabled
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
            String rationale,
            Long expectedVersion,
            @Size(max = 8) List<@NotBlank String> reasonIds,
            @Size(max = 40) String primaryReasonId
    ) {
        public CreateResolutionRequest(DecisionTypes.ResolutionKind kind, UUID selectedOptionId, String rationale, Long expectedVersion) {
            this(kind, selectedOptionId, rationale, expectedVersion, null, null);
        }
    }

    public record BeginRevisionRequest(@NotNull @Min(0) Long expectedVersion, @NotNull DecisionTypes.EditMode mode, Boolean notPurchased) {
        public BeginRevisionRequest(Long version, DecisionTypes.EditMode mode) { this(version, mode, null); }
    }
    public record VersionRequest(@NotNull @Min(0) Long expectedVersion) {}
    public record PurchaseRequest(@NotNull @Min(0) Long expectedVersion, @NotNull UUID resolutionId, @NotNull Resolution.PurchaseStatus status) {}

    public record ResolutionData(
            UUID id,
            DecisionTypes.ResolutionKind kind,
            UUID selectedOptionId,
            String rationale,
            Instant createdAt,
            Comparison.Snapshot comparisonSnapshot,
            DecisionTypes.EditMode editMode,
            UUID previousResolutionId,
            Instant supersededAt,
            List<String> reasonIds, String primaryReasonId, Resolution.PurchaseStatus purchaseStatus, Instant purchaseRecordedAt
    ) {
    }

    public record CreateOutcomeRequest(
            @Min(1) @Max(5) int satisfaction,
            @NotNull DecisionTypes.ExpectationStatus expectationStatus,
            String notes,
            Boolean wouldChooseAgain,
            String workedWell,
            String disappointedBy,
            UUID resolutionId,
            Long expectedVersion
    ) {
        public CreateOutcomeRequest(int satisfaction, DecisionTypes.ExpectationStatus expectationStatus, String notes,
                                    Boolean wouldChooseAgain, String workedWell, String disappointedBy) {
            this(satisfaction, expectationStatus, notes, wouldChooseAgain, workedWell, disappointedBy, null, null);
        }
    }

    public record OutcomeData(
            UUID id,
            int satisfaction,
            DecisionTypes.ExpectationStatus expectationStatus,
            String notes,
            Boolean wouldChooseAgain,
            String workedWell,
            String disappointedBy,
            Instant createdAt,
            UUID resolutionId
    ) {
    }

    public record GuidanceData(
            String intentText,
            boolean aiConsent,
            boolean webConsent,
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
            UUID id, String name, String url, String reason, String status,
            List<com.decisionassistant.guidance.DiscoveryContracts.ProductFact> facts, Instant fetchedAt, String sourceExcerpt,
            List<com.decisionassistant.guidance.DiscoveryContracts.RequirementCheck> requirementChecks,
            com.decisionassistant.guidance.DiscoveryContracts.OfficialOffer officialOffer,
            com.decisionassistant.guidance.OfficialStorePolicy.SourceKind sourceKind,
            com.decisionassistant.guidance.DiscoveryRanking.Group recommendationGroup,
            com.decisionassistant.guidance.DiscoveryContracts.RequirementCheck compatibilityCheck,
            String priceStatus
    ) {
    }

    public record PreferenceData(
            UUID id, DecisionTypes.TemplateKey templateKey, String criterionName, String statement,
            int strength, String status, String confidence, UUID sourceDecisionId
    ) {
    }

    public record ReminderData(UUID id, Instant dueAt, String status, String channel) {
    }

    public record AddSourceRequest(@NotBlank @Size(max = 2048) String url, @Size(max = 200) String name) {
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
