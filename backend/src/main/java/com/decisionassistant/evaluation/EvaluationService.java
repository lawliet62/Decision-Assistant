package com.decisionassistant.evaluation;

import com.decisionassistant.ai.AiCallExecutor;
import com.decisionassistant.ai.AiContracts;
import com.decisionassistant.ai.AiDataPolicy;
import com.decisionassistant.ai.AiProperties;
import com.decisionassistant.common.ConflictException;
import com.decisionassistant.common.NotFoundException;
import com.decisionassistant.decision.DecisionService;
import com.decisionassistant.decision.DecisionTaskWriter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static com.decisionassistant.evaluation.EvaluationDtos.*;

@Service
public class EvaluationService {

    static final String PROMPT_VERSION = "assessment-v1";
    static final String SCHEMA_VERSION = "llm-assessment-v1";

    private final SnapshotLoader snapshots;
    private final DecisionService decisions;
    private final AlgorithmEvaluator algorithm;
    private final EvaluationRepository evaluations;
    private final ObjectMapper objectMapper;
    private final AiContracts.DecisionAssessor assessor;
    private final AiCallExecutor aiExecutor;
    private final AiProperties aiProperties;
    private final AiDataPolicy aiDataPolicy;
    private final DecisionTaskWriter writer;
    private final Clock clock;

    public EvaluationService(SnapshotLoader snapshots, DecisionService decisions, AlgorithmEvaluator algorithm,
                             EvaluationRepository evaluations, ObjectMapper objectMapper,
                             ObjectProvider<AiContracts.DecisionAssessor> assessor,
                             AiCallExecutor aiExecutor, AiProperties aiProperties, AiDataPolicy aiDataPolicy,
                             DecisionTaskWriter writer) {
        this.snapshots = snapshots;
        this.decisions = decisions;
        this.algorithm = algorithm;
        this.evaluations = evaluations;
        this.objectMapper = objectMapper;
        this.assessor = assessor.getIfAvailable();
        this.aiExecutor = aiExecutor;
        this.aiProperties = aiProperties;
        this.aiDataPolicy = aiDataPolicy;
        this.writer = writer;
        this.clock = Clock.systemUTC();
    }

    public EvaluationResponse create(UUID decisionId, CreateEvaluationRequest request) {
        if (request.includeLlm()) {
            aiDataPolicy.validateSensitiveDataAcknowledgement(Boolean.TRUE.equals(request.sensitiveDataAcknowledged()));
            var decision = decisions.required(decisionId);
            if (decision.intentText() != null && !decision.aiConsent()) {
                throw new ConflictException("이 Decision에는 AI 처리 동의가 없습니다.");
            }
        }
        var snapshot = snapshots.load(decisionId, request.expectedVersion());
        var algorithmResult = algorithm.evaluate(snapshot);
        var inputNode = objectMapper.valueToTree(snapshot);
        var evaluation = new Evaluation(
                decisionId, snapshot.decisionVersion(), snapshot, hash(inputNode),
                algorithmResult, request.includeLlm(), Instant.now(clock));
        writer.write(decisionId, parent -> {
            if (parent.version() != request.expectedVersion()) {
                throw new ConflictException("Decision version이 변경되었습니다. 최신 draft에서 다시 평가하세요.");
            }
            return evaluations.saveAndFlush(evaluation);
        });
        if (request.includeLlm()) {
            runLlm(evaluation, snapshot);
        }
        return response(evaluations.findById(evaluation.id()).orElseThrow());
    }

    public EvaluationResponse get(UUID decisionId, UUID evaluationId) {
        return response(required(decisionId, evaluationId));
    }

    public EvaluationResponse retry(UUID decisionId, UUID evaluationId, RetryLlmRequest request) {
        aiDataPolicy.validateSensitiveDataAcknowledgement(Boolean.TRUE.equals(request.sensitiveDataAcknowledged()));
        var evaluation = required(decisionId, evaluationId);
        if (evaluation.llmStatus() == Evaluation.LlmStatus.SUCCEEDED) {
            throw new ConflictException("이미 성공한 LLM 평가는 retry하지 않습니다.");
        }
        writer.write(decisionId, parent -> {
            evaluation.llmPending();
            return evaluations.saveAndFlush(evaluation);
        });
        runLlm(evaluation, evaluation.inputSnapshot());
        return response(evaluations.findById(evaluation.id()).orElseThrow());
    }

    private void runLlm(Evaluation evaluation, DecisionSnapshot snapshot) {
        var started = Instant.now(clock);
        if (!aiProperties.enabled() || assessor == null) {
            complete(evaluation, item -> item.llmFailed("AI가 비활성화되어 있습니다.", aiProperties.provider(), aiProperties.model(),
                    PROMPT_VERSION, SCHEMA_VERSION, 0));
            return;
        }
        AiContracts.AiResult<AiContracts.LlmAssessment> result;
        try {
            result = aiExecutor.call(() -> assessor.assess(snapshot));
            validateAssessment(result.value(), snapshot);
        } catch (RuntimeException exception) {
            complete(evaluation, item -> item.llmFailed(safeMessage(exception), aiProperties.provider(), aiProperties.model(),
                    PROMPT_VERSION, SCHEMA_VERSION, Duration.between(started, Instant.now(clock)).toMillis()));
            return;
        }
        complete(evaluation, item -> item.llmSucceeded(
                result.value(), aiProperties.provider(), aiProperties.model(),
                PROMPT_VERSION, SCHEMA_VERSION, Duration.between(started, Instant.now(clock)).toMillis(),
                result.inputTokens(), result.outputTokens()));
    }

    private void complete(Evaluation evaluation, java.util.function.Consumer<Evaluation> update) {
        writer.write(evaluation.decisionId(), parent -> {
            update.accept(evaluation);
            return evaluations.saveAndFlush(evaluation);
        });
    }

    private void validateAssessment(AiContracts.LlmAssessment assessment, DecisionSnapshot snapshot) {
        if (assessment.abstained()) {
            if (assessment.recommendedOptionId() != null && !assessment.recommendedOptionId().isBlank()) {
                throw new IllegalArgumentException("abstained assessment cannot recommend an option");
            }
            return;
        }
        UUID recommended;
        try {
            recommended = UUID.fromString(assessment.recommendedOptionId());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("LLM recommendation must be a valid Option id", exception);
        }
        if (snapshot.options().stream().noneMatch(option -> option.id().equals(recommended))) {
            throw new IllegalArgumentException("LLM recommended an Option outside the snapshot");
        }
    }

    private Evaluation required(UUID decisionId, UUID evaluationId) {
        decisions.required(decisionId);
        return evaluations.findByIdAndDecisionId(evaluationId, decisionId)
                .orElseThrow(() -> new NotFoundException("Evaluation을 찾을 수 없습니다: " + evaluationId));
    }

    private EvaluationResponse response(Evaluation evaluation) {
        return new EvaluationResponse(
                evaluation.id(), evaluation.decisionId(), evaluation.decisionVersion(), evaluation.inputHash(),
                evaluation.algorithmResult(), evaluation.llmStatus(), evaluation.llmResult(),
                evaluation.llmError(), evaluation.provider(), evaluation.model(), evaluation.promptVersion(),
                evaluation.schemaVersion(), evaluation.llmLatencyMs(), evaluation.inputTokens(),
                evaluation.outputTokens(), evaluation.createdAt());
    }

    private String hash(JsonNode node) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(node.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
