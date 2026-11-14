package com.decisionassistant.guidance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceImportRepository extends JpaRepository<EvidenceImport, UUID> {
    List<EvidenceImport> findByDecisionIdOrderByCreatedAtAsc(UUID decisionId);
    @org.springframework.data.jpa.repository.Query(value = "select e.* from evidence_imports e join decisions d on d.id = e.decision_id where e.status = :#{#status.name()} and d.deleted_at is null order by e.created_at limit 1", nativeQuery = true)
    Optional<EvidenceImport> findFirstByStatusOrderByCreatedAtAsc(EvidenceImport.Status status);
    long countByDecisionIdAndStatusIn(UUID decisionId, List<EvidenceImport.Status> statuses);
    Optional<EvidenceImport> findByIdAndDecisionId(UUID id, UUID decisionId);
}
