package com.decisionassistant.preference;

import com.decisionassistant.decision.DecisionTypes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreferenceMemoryRepository extends JpaRepository<PreferenceMemory, UUID> {
    @org.springframework.data.jpa.repository.Query("""
            select m from PreferenceMemory m where m.ownerUserId = :ownerUserId and
            (m.sourceDecisionId is null or exists (select d.id from Decision d
            where d.id = m.sourceDecisionId and d.deletedAt is null)) order by m.updatedAt desc
            """)
    List<PreferenceMemory> findAllByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId);
    @org.springframework.data.jpa.repository.Query("""
            select m from PreferenceMemory m where m.ownerUserId = :ownerUserId and m.status = :status
            and m.templateKey = :templateKey and (m.sourceDecisionId is null or exists
            (select d.id from Decision d where d.id = m.sourceDecisionId and d.deletedAt is null and d.personalizationEnabled = true))
            """)
    List<PreferenceMemory> findByOwnerUserIdAndStatusAndTemplateKey(
            UUID ownerUserId, PreferenceMemory.Status status, DecisionTypes.TemplateKey templateKey);
    Optional<PreferenceMemory> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
