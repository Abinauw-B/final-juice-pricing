package com.retailpos.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByProductIdOrderByCreatedAtDesc(Long productId);
    List<PriceHistory> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
    java.util.Optional<PriceHistory> findFirstByProductIdOrderByCreatedAtDesc(Long productId);
    List<PriceHistory> findAllByOrderByCreatedAtDesc();
    Page<PriceHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
