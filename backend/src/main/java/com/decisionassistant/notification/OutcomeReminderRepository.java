package com.decisionassistant.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutcomeReminderRepository extends JpaRepository<OutcomeReminder, UUID> {
    List<OutcomeReminder> findByDecisionIdOrderByDueAtAsc(UUID decisionId);
    List<OutcomeReminder> findTop10ByStatusAndDueAtLessThanEqualOrderByDueAtAsc(OutcomeReminder.Status status, Instant dueAt);
    Optional<OutcomeReminder> findByIdAndDecisionId(UUID id, UUID decisionId);
}
