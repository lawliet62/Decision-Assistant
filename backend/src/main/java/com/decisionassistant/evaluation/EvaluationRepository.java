package com.decisionassistant.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {
    Optional<Evaluation> findByIdAndDecisionId(UUID id, UUID decisionId);
}
