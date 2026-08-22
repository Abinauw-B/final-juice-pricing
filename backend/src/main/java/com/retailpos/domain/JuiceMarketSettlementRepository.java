package com.retailpos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JuiceMarketSettlementRepository extends JpaRepository<JuiceMarketSettlement, Long> {
    Optional<JuiceMarketSettlement> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
}
