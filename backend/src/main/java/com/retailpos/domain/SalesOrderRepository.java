package com.retailpos.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    List<SalesOrder> findByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT o FROM SalesOrder o LEFT JOIN FETCH o.items WHERE o.idempotencyKey = :idempotencyKey")
    java.util.Optional<SalesOrder> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Query("SELECT o FROM SalesOrder o LEFT JOIN FETCH o.items WHERE o.orderNumber = :orderNumber")
    java.util.Optional<SalesOrder> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);

    @Query("SELECT o FROM SalesOrder o LEFT JOIN FETCH o.items WHERE o.id = :id")
    java.util.Optional<SalesOrder> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT COUNT(so) FROM SalesOrder so WHERE so.createdAt >= :since")
    Long countOrdersSince(@Param("since") LocalDateTime since);

    @Query(value = "SELECT DISTINCT o FROM SalesOrder o WHERE " +
           "(:paymentStatus IS NULL OR LOWER(o.paymentStatus) = :paymentStatus) AND " +
           "(:startDate IS NULL OR o.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR o.createdAt <= :endDate) AND " +
           "(:searchPattern IS NULL OR LOWER(o.orderNumber) LIKE :searchPattern OR CAST(o.id AS string) = :searchExact OR EXISTS (SELECT 1 FROM SalesOrderItem item WHERE item.salesOrder = o AND LOWER(item.productName) LIKE :searchPattern))",
           countQuery = "SELECT COUNT(DISTINCT o) FROM SalesOrder o WHERE " +
           "(:paymentStatus IS NULL OR LOWER(o.paymentStatus) = :paymentStatus) AND " +
           "(:startDate IS NULL OR o.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR o.createdAt <= :endDate) AND " +
           "(:searchPattern IS NULL OR LOWER(o.orderNumber) LIKE :searchPattern OR CAST(o.id AS string) = :searchExact OR EXISTS (SELECT 1 FROM SalesOrderItem item WHERE item.salesOrder = o AND LOWER(item.productName) LIKE :searchPattern))")
    Page<SalesOrder> findWithFilters(
            @Param("paymentStatus") String paymentStatus,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("searchPattern") String searchPattern,
            @Param("searchExact") String searchExact,
            Pageable pageable);

    @Query("SELECT COUNT(DISTINCT o) FROM SalesOrder o WHERE " +
           "(:paymentStatus IS NULL OR LOWER(o.paymentStatus) = :paymentStatus) AND " +
           "(:startDate IS NULL OR o.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR o.createdAt <= :endDate) AND " +
           "(:searchPattern IS NULL OR LOWER(o.orderNumber) LIKE :searchPattern OR CAST(o.id AS string) = :searchExact OR EXISTS (SELECT 1 FROM SalesOrderItem item WHERE item.salesOrder = o AND LOWER(item.productName) LIKE :searchPattern))")
    Long countWithFilters(
            @Param("paymentStatus") String paymentStatus,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("searchPattern") String searchPattern,
            @Param("searchExact") String searchExact);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM SalesOrder o WHERE " +
           "(:paymentStatus IS NULL OR LOWER(o.paymentStatus) = :paymentStatus) AND " +
           "(:startDate IS NULL OR o.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR o.createdAt <= :endDate) AND " +
           "(:searchPattern IS NULL OR LOWER(o.orderNumber) LIKE :searchPattern OR CAST(o.id AS string) = :searchExact OR EXISTS (SELECT 1 FROM SalesOrderItem item WHERE item.salesOrder = o AND LOWER(item.productName) LIKE :searchPattern))")
    BigDecimal sumTotalAmountWithFilters(
            @Param("paymentStatus") String paymentStatus,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("searchPattern") String searchPattern,
            @Param("searchExact") String searchExact);
}

