package com.retailpos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {

    @Query("SELECT COALESCE(SUM(soi.quantity), 0) FROM SalesOrderItem soi JOIN soi.salesOrder so WHERE soi.productId = :productId AND so.createdAt >= :since")
    Integer countQuantitySoldForProductSince(@Param("productId") Long productId, @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(soi.quantity), 0) FROM SalesOrderItem soi JOIN soi.salesOrder so WHERE soi.productId = :productId AND so.createdAt >= :startTime AND so.createdAt < :endTime")
    Integer countQuantitySoldForProductBetween(@Param("productId") Long productId, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Query("SELECT soi FROM SalesOrderItem soi JOIN soi.salesOrder so WHERE so.createdAt >= :since")
    List<SalesOrderItem> findItemsSoldSince(@Param("since") LocalDateTime since);
}
