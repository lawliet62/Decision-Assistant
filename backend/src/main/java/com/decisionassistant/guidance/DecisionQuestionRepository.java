package com.decisionassistant.guidance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface DecisionQuestionRepository extends JpaRepository<DecisionQuestion, UUID> {
    List<DecisionQuestion> findTop3ByDecisionIdAndStatusOrderByPriorityAscCreatedAtAsc(
            UUID decisionId, DecisionQuestion.Status status);
    List<DecisionQuestion> findByDecisionIdOrderByPriorityAscCreatedAtAsc(UUID decisionId);
    Optional<DecisionQuestion> findByIdAndDecisionId(UUID id, UUID decisionId);
}
