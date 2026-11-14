package com.decisionassistant.guidance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProductDiscoveryRepository extends JpaRepository<ProductDiscovery, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ProductDiscovery d where d.decisionId = :id")
    Optional<ProductDiscovery> lockById(UUID id);
    @Query(value = "select * from product_discoveries where status = 'PENDING' and exists (select 1 from decisions d where d.id = decision_id and d.deleted_at is null) order by updated_at limit 1 for update skip locked", nativeQuery = true)
    Optional<ProductDiscovery> claimNext();
    @Modifying
    @Query("update ProductDiscovery d set d.status = 'FAILED', d.stage = null, d.error = '추천 처리가 중단되었습니다. 다시 시도하세요.', d.version = d.version + 1 where d.status = 'RUNNING' and d.updatedAt < :before")
    int failInterrupted(Instant before);
}
