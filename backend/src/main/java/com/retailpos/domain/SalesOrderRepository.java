package com.retailpos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    List<SalesOrder> findByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT o FROM SalesOrder o LEFT JOIN FETCH o.items WHERE o.idempotencyKey = :idempotencyKey")
    java.util.Optional<SalesOrder> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Query("SELECT o FROM SalesOrder o LEFT JOIN FETCH o.items WHERE o.orderNumber = :orderNumber")
    java.util.Optional<SalesOrder> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);

    @Query("SELECT COUNT(so) FROM SalesOrder so WHERE so.createdAt >= :since")
    Long countOrdersSince(@Param("since") LocalDateTime since);
}
