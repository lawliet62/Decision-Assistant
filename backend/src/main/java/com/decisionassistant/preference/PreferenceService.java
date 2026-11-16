package com.decisionassistant.preference;

import com.decisionassistant.common.InvalidDecisionException;
import com.decisionassistant.common.NotFoundException;
import com.decisionassistant.identity.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PreferenceService {
    private final PreferenceMemoryRepository memories;
    private final CurrentUserProvider currentUser;
    private final com.decisionassistant.decision.DecisionRepository decisions;
    private final Clock clock = Clock.systemUTC();
    public PreferenceService(PreferenceMemoryRepository memories, CurrentUserProvider currentUser,
                             com.decisionassistant.decision.DecisionRepository decisions) {
        this.memories = memories; this.currentUser = currentUser; this.decisions = decisions;
    }
    @Transactional(readOnly = true)
    public List<PreferenceDtos.PreferenceResponse> list() {
        return memories.findAllByOwnerUserIdOrderByUpdatedAtDesc(currentUser.current().id()).stream()
                .map(this::response).toList();
    }
    @Transactional
    public PreferenceDtos.PreferenceResponse update(UUID id, PreferenceDtos.UpdatePreferenceRequest request) {
        var item = required(id); var now = Instant.now(clock);
        switch (request.action().toUpperCase(java.util.Locale.ROOT)) {
            case "CONFIRM" -> item.confirm(now);
            case "DISMISS" -> item.dismiss(now);
            case "EDIT" -> {
                if (request.statement() == null || request.statement().isBlank() || request.strength() == null)
                    throw new InvalidDecisionException(List.of("EDIT에는 statement와 strength가 필요합니다."));
                item.update(request.statement().trim(), request.strength(), now);
            }
            default -> throw new InvalidDecisionException(List.of("action은 CONFIRM, DISMISS 또는 EDIT여야 합니다."));
        }
        return response(item);
    }
    @Transactional public void delete(UUID id) { memories.delete(required(id)); }
    private PreferenceMemory required(UUID id) {
        var owner = currentUser.current().id();
        var item = memories.findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("성향 기억을 찾을 수 없습니다."));
        if (item.sourceDecisionId() != null) decisions.lockByIdAndOwner(item.sourceDecisionId(), owner)
                .orElseThrow(() -> new NotFoundException("원본 결정을 찾을 수 없습니다."));
        return memories.findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("정정된 기록의 성향 기억은 사용할 수 없습니다."));
    }
    private PreferenceDtos.PreferenceResponse response(PreferenceMemory item) {
        return new PreferenceDtos.PreferenceResponse(item.id(), item.templateKey(), item.criterionName(),
                item.statement(), item.strength(), item.status().name(), item.confidence().name(), item.sourceDecisionId());
    }
}
