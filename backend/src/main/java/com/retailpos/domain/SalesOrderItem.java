package com.retailpos.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sales_order_items")
public class SalesOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private SalesOrder salesOrder;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "cup_size_ml", nullable = false)
    private Integer cupSizeMl;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "locked_price", nullable = false)
    private BigDecimal lockedPrice;

    @Column(name = "price_version", nullable = false)
    private Integer priceVersion = 1;

    @Column(name = "volume_deducted_ml", nullable = false)
    private Integer volumeDeductedMl;

    @Column(name = "created_at", insertable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    public SalesOrderItem() {}

    public SalesOrderItem(Long id, SalesOrder salesOrder, Long productId, String productName, Integer cupSizeMl, BigDecimal unitPrice, BigDecimal lockedPrice, Integer priceVersion, Integer quantity, BigDecimal totalPrice, Integer volumeDeductedMl, java.time.LocalDateTime createdAt) {
        this.id = id;
        this.salesOrder = salesOrder;
        this.productId = productId;
        this.productName = productName;
        this.cupSizeMl = cupSizeMl;
        this.unitPrice = unitPrice;
        this.lockedPrice = lockedPrice != null ? lockedPrice : unitPrice;
        this.priceVersion = priceVersion != null ? priceVersion : 1;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.volumeDeductedMl = volumeDeductedMl;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public SalesOrder getSalesOrder() { return salesOrder; }
    public void setSalesOrder(SalesOrder salesOrder) { this.salesOrder = salesOrder; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public Integer getCupSizeMl() { return cupSizeMl; }
    public void setCupSizeMl(Integer cupSizeMl) { this.cupSizeMl = cupSizeMl; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getLockedPrice() { return lockedPrice; }
    public void setLockedPrice(BigDecimal lockedPrice) { this.lockedPrice = lockedPrice; }
    public Integer getPriceVersion() { return priceVersion; }
    public void setPriceVersion(Integer priceVersion) { this.priceVersion = priceVersion; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
    public Integer getVolumeDeductedMl() { return volumeDeductedMl; }
    public void setVolumeDeductedMl(Integer volumeDeductedMl) { this.volumeDeductedMl = volumeDeductedMl; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static SalesOrderItemBuilder builder() { return new SalesOrderItemBuilder(); }

    public static class SalesOrderItemBuilder {
        private Long id;
        private SalesOrder salesOrder;
        private Long productId;
        private String productName;
        private Integer cupSizeMl;
        private BigDecimal unitPrice;
        private BigDecimal lockedPrice;
        private Integer priceVersion = 1;
        private Integer quantity;
        private BigDecimal totalPrice;
        private Integer volumeDeductedMl;
        private java.time.LocalDateTime createdAt;

        public SalesOrderItemBuilder id(Long id) { this.id = id; return this; }
        public SalesOrderItemBuilder salesOrder(SalesOrder salesOrder) { this.salesOrder = salesOrder; return this; }
        public SalesOrderItemBuilder productId(Long productId) { this.productId = productId; return this; }
        public SalesOrderItemBuilder productName(String productName) { this.productName = productName; return this; }
        public SalesOrderItemBuilder cupSizeMl(Integer cupSizeMl) { this.cupSizeMl = cupSizeMl; return this; }
        public SalesOrderItemBuilder unitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; return this; }
        public SalesOrderItemBuilder lockedPrice(BigDecimal lockedPrice) { this.lockedPrice = lockedPrice; return this; }
        public SalesOrderItemBuilder priceVersion(Integer priceVersion) { this.priceVersion = priceVersion; return this; }
        public SalesOrderItemBuilder quantity(Integer quantity) { this.quantity = quantity; return this; }
        public SalesOrderItemBuilder totalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; return this; }
        public SalesOrderItemBuilder volumeDeductedMl(Integer volumeDeductedMl) { this.volumeDeductedMl = volumeDeductedMl; return this; }
        public SalesOrderItemBuilder createdAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public SalesOrderItem build() {
            return new SalesOrderItem(id, salesOrder, productId, productName, cupSizeMl, unitPrice, lockedPrice, priceVersion, quantity, totalPrice, volumeDeductedMl, createdAt);
        }
    }
}
