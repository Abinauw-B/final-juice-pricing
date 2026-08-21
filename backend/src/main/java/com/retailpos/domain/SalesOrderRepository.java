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

    java.util.Optional<SalesOrder> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COUNT(so) FROM SalesOrder so WHERE so.createdAt >= :since")
    Long countOrdersSince(@Param("since") LocalDateTime since);
}
