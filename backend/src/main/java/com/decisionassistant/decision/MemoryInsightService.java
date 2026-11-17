package com.decisionassistant.decision;

import com.decisionassistant.ai.*;
import com.decisionassistant.common.*;
import com.decisionassistant.identity.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.decisionassistant.decision.MemoryInsightContracts.*;

@Service
public class MemoryInsightService {
    private final DecisionService decisions;
    private final DecisionMemoryController memories;
    private final DecisionRepository repository;
    private final CurrentUserProvider user;
    private final EntityManager em;
    private final ObjectMapper mapper;
    private final Interpreter interpreter;
    private final AiProperties properties;
    private final AiCallExecutor executor;
    private final AiDataPolicy policy;
    private final TransactionTemplate tx;
    public MemoryInsightService(DecisionService decisions, DecisionMemoryController memories, DecisionRepository repository,
            CurrentUserProvider user, EntityManager em, ObjectMapper mapper, ObjectProvider<Interpreter> interpreter,
            AiProperties properties, AiCallExecutor executor, AiDataPolicy policy, PlatformTransactionManager manager) {
        this.decisions = decisions; this.memories = memories; this.repository = repository; this.user = user;
        this.em = em; this.mapper = mapper; this.interpreter = interpreter.getIfAvailable(); this.properties = properties;
        this.executor = executor; this.policy = policy; tx = new TransactionTemplate(manager);
    }
    public InsightView view(UUID id, UUID resolution) {
        return tx.execute(status -> {
            var input = input(id, resolution);
            return new InsightView(properties.enabled() && interpreter != null, properties.provider(), properties.model(),
                    policy.dataNotice(), input, hash(input), saved(id, resolution, hash(input)));
        });
    }
    public InsightResponse generate(UUID id, UUID resolution, String expectedInputHash) {
        var preview = view(id, resolution);
        if (!preview.inputHash().equals(expectedInputHash))
            throw new ConflictException("전송 대상이 변경되었습니다. 전송 내용을 다시 확인하세요.");
        if (preview.saved() != null) return preview.saved();
        if (!preview.available()) throw new InvalidDecisionException(List.of("AI 요약이 현재 비활성입니다. 원문 기록을 참고하세요."));
        var input = preview.input();
        var fingerprint = hash(input);
        var owner = user.current().id();
        var frozen = tx.execute(status -> {
            lockParents(id, input.source().decisionId(), owner);
            if (!hash(input(id, resolution)).equals(fingerprint))
                throw new ConflictException("전송 대상이 변경되었습니다. 전송 내용을 다시 확인하세요.");
            var snapshot = new MemoryInsightInput(id, fingerprint, input);
            em.persist(snapshot);
            em.flush();
            return snapshot;
        });
        long start = System.nanoTime();
        final AiContracts.AiResult<Insight> result;
        try { result = executor.call(() -> interpreter.interpret(frozen.snapshot)); }
        catch (RuntimeException failure) {
            throw new InvalidDecisionException(List.of("AI 요약을 생성하지 못했습니다. 원문은 그대로 참고할 수 있습니다."));
        }
        validate(result.value());
        return tx.execute(status -> {
            // 외부 호출 동안 잠금을 보유하지 않는다. 삭제·정정과 같은 부모 잠금으로 결과 쓰기만 직렬화한다.
            lockParents(id, input.source().decisionId(), owner);
            if (!hash(input(id, resolution)).equals(fingerprint))
                throw new ConflictException("현재 결정 또는 과거 기록이 변경되었습니다. 내용을 확인하고 다시 요청하세요.");
            var existing = saved(id, resolution, fingerprint);
            if (existing != null) return existing;
            var row = new MemoryInsight(id, input.source().decisionId(), resolution, frozen.id, fingerprint, result.value(),
                    properties.provider(), properties.model(), result.inputTokens(), result.outputTokens(),
                    (System.nanoTime() - start) / 1_000_000);
            em.persist(row);
            return response(row);
        });
    }
    private void lockParents(UUID current, UUID source, UUID owner) {
        java.util.stream.Stream.of(current, source).distinct().sorted().forEach(key ->
                repository.lockByIdAndOwner(key, owner).orElseThrow(() -> new NotFoundException("근거 결정을 찾을 수 없습니다.")));
        em.clear();
    }
    private Input input(UUID id, UUID resolution) {
        var current = decisions.get(id);
        var source = memories.list(id).stream().filter(m -> m.resolutionId().equals(resolution)).findFirst()
                .orElseThrow(() -> new NotFoundException("활용 가능한 과거 기록을 찾을 수 없습니다."));
        // 다른 기록에서 복사한 현재 메모는 재해석의 근거로 보내지 않는다.
        var context = current.context();
        return new Input(current.title(), context == null ? null : new DecisionDtos.ContextData(context.intendedUse(), context.budget(), context.currency(), null), current.comparison() == null ? null : current.comparison().settings(), source,
                DecisionReasons.templates().stream().filter(r -> source.reasonIds().contains(r.id())).toList());
    }
    private String hash(Input input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (PROMPT_VERSION + mapper.writeValueAsString(input)).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("기록 식별 실패", e); }
    }
    private InsightResponse saved(UUID id, UUID resolution, String fingerprint) {
        return em.createQuery("select m from MemoryInsight m where m.decisionId = :id and m.resolutionId = :r and m.inputHash = :h", MemoryInsight.class)
                .setParameter("id", id).setParameter("r", resolution).setParameter("h", fingerprint).getResultStream()
                .findFirst().map(this::response).orElse(null);
    }
    private InsightResponse response(MemoryInsight row) {
        return new InsightResponse(row.sourceDecisionId, row.resolutionId, row.result, row.provider, row.model, row.promptVersion, row.createdAt);
    }
    static void validate(Insight value) {
        if (value == null || !valid(value.summary()) || !valid(value.relevance()) || !valid(value.uncertainty())
                || value.conditions() == null || value.conditions().size() > 4 || value.conditions().stream().anyMatch(s -> !valid(s)))
            throw new InvalidDecisionException(List.of("AI 요약 형식이 올바르지 않습니다. 원문 기록을 참고하세요."));
    }
    private static boolean valid(String value) { return value != null && !value.isBlank() && value.length() <= 600; }
}
