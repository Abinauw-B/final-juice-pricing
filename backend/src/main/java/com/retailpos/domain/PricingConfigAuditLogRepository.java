package com.retailpos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingConfigAuditLogRepository extends JpaRepository<PricingConfigAuditLog, Long> {

    List<PricingConfigAuditLog> findAllByOrderByCreatedAtDesc();

    List<PricingConfigAuditLog> findBySettingKeyOrderByCreatedAtDesc(String settingKey);

    List<PricingConfigAuditLog> findByProductIdOrderByCreatedAtDesc(Long productId);
}
