package com.decisionassistant.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutcomeCheckInRepository extends JpaRepository<OutcomeCheckIn, UUID> {
    List<OutcomeCheckIn> findByDecisionIdOrderByCreatedAtDesc(UUID decisionId);
}
