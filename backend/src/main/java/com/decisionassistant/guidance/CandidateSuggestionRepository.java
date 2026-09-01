package com.decisionassistant.guidance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateSuggestionRepository extends JpaRepository<CandidateSuggestion, UUID> {
    List<CandidateSuggestion> findByDecisionIdOrderByCreatedAtAsc(UUID decisionId);
    Optional<CandidateSuggestion> findByIdAndDecisionId(UUID id, UUID decisionId);
    boolean existsByDecisionIdAndUrl(UUID decisionId, String url);
}
