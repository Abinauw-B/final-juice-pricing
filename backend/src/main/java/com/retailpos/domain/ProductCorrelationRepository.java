package com.retailpos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCorrelationRepository extends JpaRepository<ProductCorrelation, Long> {

    List<ProductCorrelation> findBySourceProductIdAndEnabledTrue(Long sourceProductId);

    List<ProductCorrelation> findByEnabledTrue();

    Optional<ProductCorrelation> findBySourceProductIdAndTargetProductId(Long sourceProductId, Long targetProductId);
}
