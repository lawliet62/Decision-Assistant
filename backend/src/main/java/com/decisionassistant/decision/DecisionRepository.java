package com.decisionassistant.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    List<Decision> findAllByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId);

    Optional<Decision> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
