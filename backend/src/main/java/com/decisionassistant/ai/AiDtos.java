package com.decisionassistant.ai;

import jakarta.validation.constraints.AssertTrue;

import java.time.Instant;
import java.util.UUID;

public final class AiDtos {

    private AiDtos() {
    }

    public record CreateStructureProposalRequest(
            long expectedVersion,
            @AssertTrue(message = "AI 전송 동의가 필요합니다.") boolean aiConsent,
            Boolean sensitiveDataAcknowledged
    ) {
    }

    public record StructureRunResponse(
            UUID id,
            UUID decisionId,
            long decisionVersion,
            StructureRun.Status status,
            AiContracts.StructureProposal proposal,
            String error,
            String provider,
            String model,
            String promptVersion,
            String schemaVersion,
            Long latencyMs,
            Long inputTokens,
            Long outputTokens,
            Instant createdAt
    ) {
    }

    public record AiCapabilitiesResponse(
            boolean enabled,
            String provider,
            String model,
            boolean requiresSensitiveDataAcknowledgement,
            String dataNotice
    ) {
    }
}
