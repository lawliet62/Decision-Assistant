package com.decisionassistant.evaluation;

import com.decisionassistant.ai.AiContracts;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

import java.time.Instant;
import java.util.UUID;

public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    public record CreateEvaluationRequest(
            long expectedVersion,
            boolean includeLlm,
            boolean aiConsent,
            Boolean sensitiveDataAcknowledged
    ) {
        @AssertTrue(message = "LLM 평가를 요청할 때는 aiConsent가 필요합니다.")
        @JsonIgnore
        @Schema(hidden = true)
        public boolean isConsentValid() {
            return !includeLlm || aiConsent;
        }
    }

    public record RetryLlmRequest(
            @AssertTrue(message = "AI 전송 동의가 필요합니다.") boolean aiConsent,
            Boolean sensitiveDataAcknowledged
    ) {
    }

    public record EvaluationResponse(
            UUID id,
            UUID decisionId,
            long decisionVersion,
            String inputHash,
            AlgorithmResult algorithmResult,
            Evaluation.LlmStatus llmStatus,
            AiContracts.LlmAssessment llmAssessment,
            String llmError,
            String provider,
            String model,
            String promptVersion,
            String schemaVersion,
            Long llmLatencyMs,
            Long inputTokens,
            Long outputTokens,
            Instant createdAt
    ) {
    }
}
