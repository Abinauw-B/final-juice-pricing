package com.retailpos.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_crash_snapshots", uniqueConstraints = {
    @UniqueConstraint(name = "uq_crash_code_product", columnNames = {"crash_code", "product_id"})
})
public class MarketCrashSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crash_code", nullable = false)
    private String crashCode;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "pre_crash_price", nullable = false)
    private BigDecimal preCrashPrice;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public MarketCrashSnapshot() {}

    public MarketCrashSnapshot(Long id, String crashCode, Long productId, BigDecimal preCrashPrice, LocalDateTime createdAt) {
        this.id = id;
        this.crashCode = crashCode;
        this.productId = productId;
        this.preCrashPrice = preCrashPrice;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCrashCode() { return crashCode; }
    public void setCrashCode(String crashCode) { this.crashCode = crashCode; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public BigDecimal getPreCrashPrice() { return preCrashPrice; }
    public void setPreCrashPrice(BigDecimal preCrashPrice) { this.preCrashPrice = preCrashPrice; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static MarketCrashSnapshotBuilder builder() { return new MarketCrashSnapshotBuilder(); }

    public static class MarketCrashSnapshotBuilder {
        private Long id;
        private String crashCode;
        private Long productId;
        private BigDecimal preCrashPrice;
        private LocalDateTime createdAt = LocalDateTime.now();

        public MarketCrashSnapshotBuilder id(Long id) { this.id = id; return this; }
        public MarketCrashSnapshotBuilder crashCode(String crashCode) { this.crashCode = crashCode; return this; }
        public MarketCrashSnapshotBuilder productId(Long productId) { this.productId = productId; return this; }
        public MarketCrashSnapshotBuilder preCrashPrice(BigDecimal preCrashPrice) { this.preCrashPrice = preCrashPrice; return this; }
        public MarketCrashSnapshotBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public MarketCrashSnapshot build() {
            return new MarketCrashSnapshot(id, crashCode, productId, preCrashPrice, createdAt);
        }
    }
}
