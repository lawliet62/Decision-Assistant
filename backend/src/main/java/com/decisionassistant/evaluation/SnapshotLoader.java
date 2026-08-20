package com.decisionassistant.evaluation;

import com.decisionassistant.common.ConflictException;
import com.decisionassistant.decision.DecisionService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class SnapshotLoader {

    private final DecisionService decisions;
    private final SnapshotFactory factory;

    public SnapshotLoader(DecisionService decisions, SnapshotFactory factory) {
        this.decisions = decisions;
        this.factory = factory;
    }

    @Transactional(readOnly = true)
    public DecisionSnapshot load(UUID decisionId, long expectedVersion) {
        var decision = decisions.required(decisionId);
        if (decision.version() != expectedVersion) {
            throw new ConflictException("Decision version이 변경되었습니다. 최신 draft에서 다시 평가하세요.");
        }
        return factory.create(decision);
    }
}
