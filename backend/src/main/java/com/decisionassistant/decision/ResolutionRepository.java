package com.decisionassistant.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResolutionRepository extends JpaRepository<Resolution, UUID> {
    List<Resolution> findByDecisionIdOrderByCreatedAtDesc(UUID decisionId);
}
