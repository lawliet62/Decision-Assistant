package com.decisionassistant.guidance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceImportRepository extends JpaRepository<EvidenceImport, UUID> {
    List<EvidenceImport> findByDecisionIdOrderByCreatedAtAsc(UUID decisionId);
    Optional<EvidenceImport> findFirstByStatusOrderByCreatedAtAsc(EvidenceImport.Status status);
    long countByDecisionIdAndStatusIn(UUID decisionId, List<EvidenceImport.Status> statuses);
    Optional<EvidenceImport> findByIdAndDecisionId(UUID id, UUID decisionId);
}
