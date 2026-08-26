package com.retailpos.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_config_audit_logs")
public class PricingConfigAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user", nullable = false, length = 100)
    private String adminUser = "ADMIN";

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "version_before")
    private Long versionBefore;

    @Column(name = "version_after")
    private Long versionAfter;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public PricingConfigAuditLog() {}

    public PricingConfigAuditLog(String adminUser, String settingKey, Long productId, String oldValue, String newValue, Long versionBefore, Long versionAfter, String reason) {
        this.adminUser = adminUser;
        this.settingKey = settingKey;
        this.productId = productId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.versionBefore = versionBefore;
        this.versionAfter = versionAfter;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAdminUser() { return adminUser; }
    public void setAdminUser(String adminUser) { this.adminUser = adminUser; }

    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public Long getVersionBefore() { return versionBefore; }
    public void setVersionBefore(Long versionBefore) { this.versionBefore = versionBefore; }

    public Long getVersionAfter() { return versionAfter; }
    public void setVersionAfter(Long versionAfter) { this.versionAfter = versionAfter; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
