package com.retailpos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarketEventRepository extends JpaRepository<MarketEvent, Long> {

    List<MarketEvent> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<MarketEvent> findTop50ByOrderByCreatedAtDesc();
}
