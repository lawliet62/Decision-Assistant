package com.decisionassistant.ai;

import com.decisionassistant.common.ConflictException;
import com.decisionassistant.decision.DecisionDtos;
import com.decisionassistant.decision.DecisionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.decisionassistant.ai.AiDtos.*;

@Service
public class StructureService {

    static final String PROMPT_VERSION = "structure-v2";
    static final String SCHEMA_VERSION = "structure-proposal-v1";

    private final DecisionService decisions;
    private final StructureRunRepository runs;
    private final AiContracts.DecisionStructurer structurer;
    private final AiCallExecutor executor;
    private final AiProperties properties;
    private final AiDataPolicy dataPolicy;
    private final Clock clock = Clock.systemUTC();

    public StructureService(DecisionService decisions, StructureRunRepository runs,
                            ObjectProvider<AiContracts.DecisionStructurer> structurer,
                            AiCallExecutor executor, AiProperties properties, AiDataPolicy dataPolicy) {
        this.decisions = decisions;
        this.runs = runs;
        this.structurer = structurer.getIfAvailable();
        this.executor = executor;
        this.properties = properties;
        this.dataPolicy = dataPolicy;
    }

    public StructureRunResponse create(UUID decisionId, CreateStructureProposalRequest request) {
        dataPolicy.validateSensitiveDataAcknowledgement(Boolean.TRUE.equals(request.sensitiveDataAcknowledged()));
        var decision = decisions.get(decisionId);
        if (decision.version() != request.expectedVersion()) {
            throw new ConflictException("Decision version이 변경되었습니다. 최신 draft에서 다시 요청하세요.");
        }
        var input = input(decision);
        var run = new StructureRun(decisionId, decision.version(), input, Instant.now(clock));
        runs.saveAndFlush(run);

        var started = Instant.now(clock);
        if (!properties.enabled() || structurer == null) {
            run.failed("AI가 비활성화되어 있습니다.", properties.provider(), properties.model(),
                    PROMPT_VERSION, SCHEMA_VERSION, 0);
            return response(runs.saveAndFlush(run));
        }
        try {
            var result = executor.call(() -> structurer.structure(input));
            run.succeeded(result.value(), properties.provider(), properties.model(),
                    PROMPT_VERSION, SCHEMA_VERSION, Duration.between(started, Instant.now(clock)).toMillis(),
                    result.inputTokens(), result.outputTokens());
        } catch (RuntimeException exception) {
            run.failed(exception.getMessage(), properties.provider(), properties.model(),
                    PROMPT_VERSION, SCHEMA_VERSION, Duration.between(started, Instant.now(clock)).toMillis());
        }
        return response(runs.saveAndFlush(run));
    }

    public AiCapabilitiesResponse capabilities() {
        return new AiCapabilitiesResponse(
                properties.enabled() && structurer != null,
                properties.provider(), properties.model(),
                dataPolicy.requiresSensitiveDataAcknowledgement(), dataPolicy.dataNotice());
    }

    private AiContracts.StructureInput input(DecisionDtos.DecisionResponse decision) {
        return new AiContracts.StructureInput(
                decision.title(), decision.context().intendedUse(), decision.context().notes(),
                decision.options().stream().map(option -> new AiContracts.OptionInput(
                        option.id(), option.name(), option.description(), option.sourceText())).toList(),
                decision.criteria().stream().filter(DecisionDtos.CriterionData::active)
                        .map(criterion -> new AiContracts.CriterionInput(
                                criterion.name(), criterion.kind(), criterion.unit())).toList());
    }

    private StructureRunResponse response(StructureRun run) {
        return new StructureRunResponse(
                run.id(), run.decisionId(), run.decisionVersion(), run.status(), run.result(), run.error(),
                run.provider(), run.model(), run.promptVersion(), run.schemaVersion(), run.latencyMs(),
                run.inputTokens(), run.outputTokens(), run.createdAt());
    }
}
