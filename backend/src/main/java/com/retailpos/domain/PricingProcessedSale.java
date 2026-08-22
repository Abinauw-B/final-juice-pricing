package com.retailpos.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_processed_sales")
public class PricingProcessedSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_item_id", nullable = false)
    private Long saleItemId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "settlement_id", nullable = false)
    private String settlementId;

    @Column(name = "processed_at", insertable = false, updatable = false)
    private LocalDateTime processedAt;

    public PricingProcessedSale() {}

    public PricingProcessedSale(Long saleItemId, Long orderId, Long productId, String settlementId) {
        this.saleItemId = saleItemId;
        this.orderId = orderId;
        this.productId = productId;
        this.settlementId = settlementId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSaleItemId() { return saleItemId; }
    public void setSaleItemId(Long saleItemId) { this.saleItemId = saleItemId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getSettlementId() { return settlementId; }
    public void setSettlementId(String settlementId) { this.settlementId = settlementId; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
