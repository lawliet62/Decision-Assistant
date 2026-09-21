package com.decisionassistant.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Decision d where d.id = :id and d.ownerUserId = :ownerId")
    Optional<Decision> lockByIdAndOwner(UUID id, UUID ownerId);

    List<Decision> findAllByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId);

    Optional<Decision> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
