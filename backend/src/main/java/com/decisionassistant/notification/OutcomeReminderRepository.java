package com.decisionassistant.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutcomeReminderRepository extends JpaRepository<OutcomeReminder, UUID> {
    @org.springframework.data.jpa.repository.Query(value = "select * from outcome_reminders where id = :id and status = 'PENDING' and due_at <= :now", nativeQuery = true)
    Optional<OutcomeReminder> pendingForDelivery(UUID id, Instant now);
    List<OutcomeReminder> findByDecisionIdOrderByDueAtAsc(UUID decisionId);
    @org.springframework.data.jpa.repository.Query(value = "select r.* from outcome_reminders r join decisions d on d.id = r.decision_id where r.status = :#{#status.name()} and r.due_at <= :dueAt and d.deleted_at is null order by r.due_at limit 10", nativeQuery = true)
    List<OutcomeReminder> findTop10ByStatusAndDueAtLessThanEqualOrderByDueAtAsc(OutcomeReminder.Status status, Instant dueAt);
    Optional<OutcomeReminder> findByIdAndDecisionId(UUID id, UUID decisionId);
}
