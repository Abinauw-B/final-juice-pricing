package com.retailpos.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_correlations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"source_product_id", "target_product_id"})
})
public class ProductCorrelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "source_product_id", nullable = false)
    private Product sourceProduct;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_product_id", nullable = false)
    private Product targetProduct;

    @Column(name = "correlation_coefficient", nullable = false)
    private BigDecimal correlationCoefficient = BigDecimal.ZERO;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public ProductCorrelation() {}

    public ProductCorrelation(Long id, Product sourceProduct, Product targetProduct, BigDecimal correlationCoefficient, Boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.sourceProduct = sourceProduct;
        this.targetProduct = targetProduct;
        this.correlationCoefficient = correlationCoefficient != null ? correlationCoefficient : BigDecimal.ZERO;
        this.enabled = enabled != null ? enabled : true;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Product getSourceProduct() { return sourceProduct; }
    public void setSourceProduct(Product sourceProduct) { this.sourceProduct = sourceProduct; }

    public Product getTargetProduct() { return targetProduct; }
    public void setTargetProduct(Product targetProduct) { this.targetProduct = targetProduct; }

    public BigDecimal getCorrelationCoefficient() { return correlationCoefficient; }
    public void setCorrelationCoefficient(BigDecimal correlationCoefficient) { this.correlationCoefficient = correlationCoefficient; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static ProductCorrelationBuilder builder() { return new ProductCorrelationBuilder(); }

    public static class ProductCorrelationBuilder {
        private Long id;
        private Product sourceProduct;
        private Product targetProduct;
        private BigDecimal correlationCoefficient = BigDecimal.ZERO;
        private Boolean enabled = true;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public ProductCorrelationBuilder id(Long id) { this.id = id; return this; }
        public ProductCorrelationBuilder sourceProduct(Product sourceProduct) { this.sourceProduct = sourceProduct; return this; }
        public ProductCorrelationBuilder targetProduct(Product targetProduct) { this.targetProduct = targetProduct; return this; }
        public ProductCorrelationBuilder correlationCoefficient(BigDecimal correlationCoefficient) { this.correlationCoefficient = correlationCoefficient; return this; }
        public ProductCorrelationBuilder enabled(Boolean enabled) { this.enabled = enabled; return this; }
        public ProductCorrelationBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public ProductCorrelationBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public ProductCorrelation build() {
            return new ProductCorrelation(id, sourceProduct, targetProduct, correlationCoefficient, enabled, createdAt, updatedAt);
        }
    }
}
