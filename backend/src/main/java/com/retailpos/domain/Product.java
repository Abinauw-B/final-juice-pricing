package com.retailpos.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String flavour;

    private String description;

    @Column(name = "default_cup_size_ml", nullable = false)
    private Integer defaultCupSizeMl = 250;

    @Column(name = "default_cup_price", nullable = false)
    private BigDecimal defaultCupPrice = new BigDecimal("20.00");

    @Column(name = "current_cup_price", nullable = false)
    private BigDecimal currentCupPrice = new BigDecimal("20.00");

    @Column(name = "min_cup_price", nullable = false)
    private BigDecimal minCupPrice = new BigDecimal("18.00");

    @Column(name = "max_cup_price", nullable = false)
    private BigDecimal maxCupPrice = new BigDecimal("25.00");

    @Column(name = "target_sales_per_2_minute")
    private Double targetSalesPer2Minute = 1.0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_price_change_timestamp")
    private LocalDateTime lastPriceChangeTimestamp;

    @Column(name = "price_version", nullable = false)
    private Integer priceVersion = 1;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Product() {}

    public Product(Long id, String name, String flavour, String description, Integer defaultCupSizeMl, BigDecimal defaultCupPrice, BigDecimal currentCupPrice, BigDecimal minCupPrice, BigDecimal maxCupPrice, Double targetSalesPer2Minute, LocalDateTime lastPriceChangeTimestamp, Integer priceVersion, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.flavour = flavour;
        this.description = description;
        this.defaultCupSizeMl = defaultCupSizeMl != null ? defaultCupSizeMl : 250;
        this.defaultCupPrice = defaultCupPrice != null ? defaultCupPrice : new BigDecimal("25.00");
        this.currentCupPrice = currentCupPrice != null ? currentCupPrice : new BigDecimal("25.00");
        this.minCupPrice = minCupPrice != null ? minCupPrice : new BigDecimal("18.00");
        this.maxCupPrice = maxCupPrice != null ? maxCupPrice : new BigDecimal("35.00");
        this.targetSalesPer2Minute = targetSalesPer2Minute != null ? targetSalesPer2Minute : 1.0;
        this.lastPriceChangeTimestamp = lastPriceChangeTimestamp;
        this.priceVersion = priceVersion != null ? priceVersion : 1;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFlavour() { return flavour; }
    public void setFlavour(String flavour) { this.flavour = flavour; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getDefaultCupSizeMl() { return defaultCupSizeMl; }
    public void setDefaultCupSizeMl(Integer defaultCupSizeMl) { this.defaultCupSizeMl = defaultCupSizeMl; }
    public BigDecimal getDefaultCupPrice() { return defaultCupPrice; }
    public void setDefaultCupPrice(BigDecimal defaultCupPrice) { this.defaultCupPrice = defaultCupPrice; }
    public BigDecimal getCurrentCupPrice() { return currentCupPrice; }
    public void setCurrentCupPrice(BigDecimal currentCupPrice) { this.currentCupPrice = currentCupPrice; }
    public BigDecimal getMinCupPrice() { return minCupPrice; }
    public void setMinCupPrice(BigDecimal minCupPrice) { this.minCupPrice = minCupPrice; }
    public BigDecimal getMaxCupPrice() { return maxCupPrice; }
    public void setMaxCupPrice(BigDecimal maxCupPrice) { this.maxCupPrice = maxCupPrice; }
    public Double getTargetSalesPer2Minute() { return targetSalesPer2Minute; }
    public void setTargetSalesPer2Minute(Double targetSalesPer2Minute) { this.targetSalesPer2Minute = targetSalesPer2Minute; }
    public Boolean getIsActive() { return isActive != null ? isActive : true; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getLastPriceChangeTimestamp() { return lastPriceChangeTimestamp; }
    public void setLastPriceChangeTimestamp(LocalDateTime lastPriceChangeTimestamp) { this.lastPriceChangeTimestamp = lastPriceChangeTimestamp; }
    public Integer getPriceVersion() { return priceVersion; }
    public void setPriceVersion(Integer priceVersion) { this.priceVersion = priceVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static ProductBuilder builder() { return new ProductBuilder(); }

    public static class ProductBuilder {
        private Long id;
        private String name;
        private String flavour;
        private String description;
        private Integer defaultCupSizeMl = 250;
        private BigDecimal defaultCupPrice = new BigDecimal("25.00");
        private BigDecimal currentCupPrice = new BigDecimal("25.00");
        private BigDecimal minCupPrice = new BigDecimal("18.00");
        private BigDecimal maxCupPrice = new BigDecimal("35.00");
        private Double targetSalesPer2Minute = 1.0;
        private LocalDateTime lastPriceChangeTimestamp;
        private Integer priceVersion = 1;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public ProductBuilder id(Long id) { this.id = id; return this; }
        public ProductBuilder name(String name) { this.name = name; return this; }
        public ProductBuilder flavour(String flavour) { this.flavour = flavour; return this; }
        public ProductBuilder description(String description) { this.description = description; return this; }
        public ProductBuilder defaultCupSizeMl(Integer defaultCupSizeMl) { this.defaultCupSizeMl = defaultCupSizeMl; return this; }
        public ProductBuilder defaultCupPrice(BigDecimal defaultCupPrice) { this.defaultCupPrice = defaultCupPrice; return this; }
        public ProductBuilder currentCupPrice(BigDecimal currentCupPrice) { this.currentCupPrice = currentCupPrice; return this; }
        public ProductBuilder minCupPrice(BigDecimal minCupPrice) { this.minCupPrice = minCupPrice; return this; }
        public ProductBuilder maxCupPrice(BigDecimal maxCupPrice) { this.maxCupPrice = maxCupPrice; return this; }
        public ProductBuilder targetSalesPer2Minute(Double targetSalesPer2Minute) { this.targetSalesPer2Minute = targetSalesPer2Minute; return this; }
        public ProductBuilder lastPriceChangeTimestamp(LocalDateTime lastPriceChangeTimestamp) { this.lastPriceChangeTimestamp = lastPriceChangeTimestamp; return this; }
        public ProductBuilder priceVersion(Integer priceVersion) { this.priceVersion = priceVersion; return this; }
        public ProductBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public ProductBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public Product build() {
            return new Product(id, name, flavour, description, defaultCupSizeMl, defaultCupPrice, currentCupPrice, minCupPrice, maxCupPrice, targetSalesPer2Minute, lastPriceChangeTimestamp, priceVersion, createdAt, updatedAt);
        }
    }
}
