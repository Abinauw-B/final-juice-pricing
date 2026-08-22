package com.retailpos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByProductIdOrderByCreatedAtDesc(Long productId);
    java.util.Optional<PriceHistory> findFirstByProductIdOrderByCreatedAtDesc(Long productId);
    List<PriceHistory> findAllByOrderByCreatedAtDesc();
}
