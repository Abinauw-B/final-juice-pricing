package com.retailpos.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_configurations", uniqueConstraints = {
        @UniqueConstraint(name = "uq_pricing_config_key_product", columnNames = {"setting_key", "product_id"})
})
public class PricingConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false)
    private String settingValue;

    @Column(name = "data_type", nullable = false, length = 50)
    private String dataType = "STRING";

    @Column(name = "scope", nullable = false, length = 50)
    private String scope = "GLOBAL";

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "description")
    private String description;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 1L;

    @Column(name = "updated_by", length = 100)
    private String updatedBy = "SYSTEM";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PricingConfiguration() {}

    public PricingConfiguration(String settingKey, String settingValue, String dataType, String scope, Long productId, String description, String updatedBy) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.dataType = dataType;
        this.scope = scope;
        this.productId = productId;
        this.description = description;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }

    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String settingValue) { this.settingValue = settingValue; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    @PrePersist
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
