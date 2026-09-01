package com.decisionassistant.preference;

import com.decisionassistant.decision.DecisionTypes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreferenceMemoryRepository extends JpaRepository<PreferenceMemory, UUID> {
    List<PreferenceMemory> findAllByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId);
    List<PreferenceMemory> findByOwnerUserIdAndStatusAndTemplateKey(
            UUID ownerUserId, PreferenceMemory.Status status, DecisionTypes.TemplateKey templateKey);
    Optional<PreferenceMemory> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
