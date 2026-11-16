package com.decisionassistant.preference;

import com.decisionassistant.decision.DecisionTypes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreferenceMemoryRepository extends JpaRepository<PreferenceMemory, UUID> {
    @org.springframework.data.jpa.repository.Query("""
            select m from PreferenceMemory m where m.ownerUserId = :ownerUserId and m.supersededAt is null and
            (m.sourceDecisionId is null or exists (select d.id from Decision d
            where d.id = m.sourceDecisionId and d.deletedAt is null)) order by m.updatedAt desc
            """)
    List<PreferenceMemory> findAllByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId);
    @org.springframework.data.jpa.repository.Query("""
            select m from PreferenceMemory m where m.ownerUserId = :ownerUserId and m.status = :status and m.supersededAt is null
            and m.templateKey = :templateKey and (m.sourceDecisionId is null or exists
            (select d.id from Decision d where d.id = m.sourceDecisionId and d.deletedAt is null and d.personalizationEnabled = true
            and (d.editMode is null or d.editMode <> com.decisionassistant.decision.DecisionTypes$EditMode.CORRECTION)))
            """)
    List<PreferenceMemory> findByOwnerUserIdAndStatusAndTemplateKey(
            UUID ownerUserId, PreferenceMemory.Status status, DecisionTypes.TemplateKey templateKey);
    @org.springframework.data.jpa.repository.Query("select m from PreferenceMemory m where m.id = :id and m.ownerUserId = :ownerUserId and m.supersededAt is null")
    Optional<PreferenceMemory> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
            update PreferenceMemory m set m.supersededAt = :now where m.sourceDecisionId = :decisionId
            and (m.sourceOutcomeId is null or exists (select o.id from OutcomeCheckIn o where o.id = m.sourceOutcomeId
            and (o.resolutionId is null or o.resolutionId = :resolutionId)))
            """)
    void supersedeForResolution(UUID decisionId, UUID resolutionId, java.time.Instant now);
}
