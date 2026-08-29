package com.retailpos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface PricingProcessedSaleRepository extends JpaRepository<PricingProcessedSale, Long> {

    boolean existsBySaleItemId(Long saleItemId);

    @Query("SELECT pps.saleItemId FROM PricingProcessedSale pps WHERE pps.productId = :productId")
    Set<Long> findProcessedSaleItemIdsForProduct(@Param("productId") Long productId);
}
