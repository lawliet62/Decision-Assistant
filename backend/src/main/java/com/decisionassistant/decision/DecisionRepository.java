package com.decisionassistant.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Decision d where d.deletedAt is null and d.id = :id and d.ownerUserId = :ownerId")
    Optional<Decision> lockByIdAndOwner(UUID id, UUID ownerId);

    @org.springframework.data.jpa.repository.Query("""
            select new com.decisionassistant.decision.DecisionDtos$DecisionSummary(
                d.id, d.version, d.title, d.templateKey, d.status, d.createdAt, d.updatedAt)
            from Decision d where d.deletedAt is null and d.archivedAt is null and d.ownerUserId = :owner and d.status in :statuses
            """)
    org.springframework.data.domain.Page<DecisionDtos.DecisionSummary> findSummaries(
            UUID owner, java.util.Set<DecisionTypes.DecisionStatus> statuses, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select new com.decisionassistant.decision.DecisionDtos$DecisionSummary(
                d.id, d.version, d.title, d.templateKey, d.status, d.createdAt, d.updatedAt)
            from Decision d where d.deletedAt is null and d.archivedAt is null and d.ownerUserId = :owner and d.status in :statuses order by d.id
            """)
    List<DecisionDtos.DecisionSummary> findSelection(UUID owner, java.util.Set<DecisionTypes.DecisionStatus> statuses);

    @org.springframework.data.jpa.repository.Query("""
            select new com.decisionassistant.decision.DecisionDtos$StatusCount(d.status, count(d))
            from Decision d where d.deletedAt is null and d.archivedAt is null and d.ownerUserId = :owner group by d.status
            """)
    List<DecisionDtos.StatusCount> countStatuses(UUID owner);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Decision d where d.deletedAt is null and d.id = :id")
    Optional<Decision> lockById(UUID id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "delete from decisions where id = :id", nativeQuery = true)
    void deleteAggregate(UUID id);

    @org.springframework.data.jpa.repository.Query("select d from Decision d where d.id = :id and d.ownerUserId = :ownerUserId and d.deletedAt is null")
    Optional<Decision> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
