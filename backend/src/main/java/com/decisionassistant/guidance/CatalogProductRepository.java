package com.decisionassistant.guidance;

import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

public interface CatalogProductRepository extends JpaRepository<CatalogProduct, String> {
    List<CatalogProduct> findByActiveTrueOrderByIdAsc();
    @Query(value = "select * from catalog_products where active and next_attempt_at <= :now order by next_attempt_at, id limit 1 for update skip locked", nativeQuery = true)
    Optional<CatalogProduct> claimNext(Instant now);
    @Query(value = "select * from catalog_products where active and purchase_url = :url and next_attempt_at <= :now order by next_attempt_at, id limit 1 for update skip locked", nativeQuery = true)
    Optional<CatalogProduct> claimNextDirect(Instant now, String url);
}
