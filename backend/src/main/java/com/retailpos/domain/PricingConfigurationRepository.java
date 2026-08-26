package com.retailpos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PricingConfigurationRepository extends JpaRepository<PricingConfiguration, Long> {

    Optional<PricingConfiguration> findBySettingKeyAndProductIdIsNull(String settingKey);

    Optional<PricingConfiguration> findBySettingKeyAndProductId(String settingKey, Long productId);

    List<PricingConfiguration> findByProductIdIsNull();

    List<PricingConfiguration> findByProductId(Long productId);

    List<PricingConfiguration> findByScope(String scope);
}
