package com.decisionassistant.decision;

import com.decisionassistant.common.NotFoundException;
import com.decisionassistant.identity.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Function;

@Service
public class DecisionTaskWriter {
    private final DecisionRepository decisions;
    private final CurrentUserProvider currentUser;

    public DecisionTaskWriter(DecisionRepository decisions, CurrentUserProvider currentUser) {
        this.decisions = decisions;
        this.currentUser = currentUser;
    }

    @Transactional
    public <T> T write(UUID decisionId, Function<Decision, T> action) {
        // 삭제와 같은 부모 잠금을 획득한 뒤에만 실행 기록을 변경한다.
        var decision = decisions.lockByIdAndOwner(decisionId, currentUser.current().id())
                .orElseThrow(() -> new NotFoundException("결정을 찾을 수 없습니다."));
        return action.apply(decision);
    }
}
