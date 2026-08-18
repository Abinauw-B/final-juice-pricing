package com.retailpos.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JuiceBatchRepository extends JpaRepository<JuiceBatch, Long> {

    List<JuiceBatch> findByProductIdAndStatus(Long productId, JuiceBatch.BatchStatus status);

    List<JuiceBatch> findByStatus(JuiceBatch.BatchStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM JuiceBatch b WHERE b.productId = :productId AND b.status = 'ACTIVE' ORDER BY b.id ASC")
    List<JuiceBatch> findActiveBatchesForProductWithLock(@Param("productId") Long productId);

    Optional<JuiceBatch> findFirstByProductIdAndStatusOrderByIdAsc(Long productId, JuiceBatch.BatchStatus status);

    default Optional<JuiceBatch> findFirstActiveBatchForProduct(Long productId) {
        return findFirstByProductIdAndStatusOrderByIdAsc(productId, JuiceBatch.BatchStatus.ACTIVE);
    }
}
