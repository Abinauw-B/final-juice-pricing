package com.retailpos.domain;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    List<SalesOrder> findByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT o FROM SalesOrder o LEFT JOIN FETCH o.items WHERE o.idempotencyKey = :idempotencyKey")
    java.util.Optional<SalesOrder> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Query("SELECT o FROM SalesOrder o LEFT JOIN FETCH o.items WHERE o.orderNumber = :orderNumber")
    java.util.Optional<SalesOrder> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);

    @Query("SELECT o FROM SalesOrder o LEFT JOIN FETCH o.items WHERE o.id = :id")
    java.util.Optional<SalesOrder> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT COUNT(so) FROM SalesOrder so WHERE so.createdAt >= :since")
    Long countOrdersSince(@Param("since") LocalDateTime since);

    static Specification<SalesOrder> buildSpecification(String paymentStatus, LocalDateTime startDate, LocalDateTime endDate, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (paymentStatus != null && !paymentStatus.isBlank() && !"ALL".equalsIgnoreCase(paymentStatus)) {
                predicates.add(cb.equal(cb.lower(root.get("paymentStatus")), paymentStatus.trim().toLowerCase()));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                String exact = search.trim();

                Predicate matchOrderNum = cb.like(cb.lower(root.get("orderNumber")), pattern);
                Predicate matchId = cb.equal(root.get("id").as(String.class), exact);

                Subquery<Long> sub = query.subquery(Long.class);
                Root<SalesOrderItem> itemRoot = sub.from(SalesOrderItem.class);
                sub.select(itemRoot.get("salesOrder").get("id"))
                   .where(cb.like(cb.lower(itemRoot.get("productName")), pattern));

                Predicate matchItem = root.get("id").in(sub);

                predicates.add(cb.or(matchOrderNum, matchId, matchItem));
            }

            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("items", JoinType.LEFT);
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

