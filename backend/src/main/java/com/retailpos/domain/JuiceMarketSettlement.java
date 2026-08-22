package com.retailpos.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "juice_market_settlements")
public class JuiceMarketSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_window_start", nullable = false)
    private LocalDateTime settlementWindowStart;

    @Column(name = "settlement_window_end", nullable = false)
    private LocalDateTime settlementWindowEnd;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @Column(nullable = false, length = 50)
    private String status = "COMPLETED";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public JuiceMarketSettlement() {}

    public JuiceMarketSettlement(Long id, LocalDateTime settlementWindowStart, LocalDateTime settlementWindowEnd, String idempotencyKey, String status, LocalDateTime createdAt) {
        this.id = id;
        this.settlementWindowStart = settlementWindowStart;
        this.settlementWindowEnd = settlementWindowEnd;
        this.idempotencyKey = idempotencyKey;
        this.status = status != null ? status : "COMPLETED";
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getSettlementWindowStart() { return settlementWindowStart; }
    public void setSettlementWindowStart(LocalDateTime settlementWindowStart) { this.settlementWindowStart = settlementWindowStart; }
    public LocalDateTime getSettlementWindowEnd() { return settlementWindowEnd; }
    public void setSettlementWindowEnd(LocalDateTime settlementWindowEnd) { this.settlementWindowEnd = settlementWindowEnd; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static JuiceMarketSettlementBuilder builder() { return new JuiceMarketSettlementBuilder(); }

    public static class JuiceMarketSettlementBuilder {
        private Long id;
        private LocalDateTime settlementWindowStart;
        private LocalDateTime settlementWindowEnd;
        private String idempotencyKey;
        private String status = "COMPLETED";
        private LocalDateTime createdAt;

        public JuiceMarketSettlementBuilder id(Long id) { this.id = id; return this; }
        public JuiceMarketSettlementBuilder settlementWindowStart(LocalDateTime settlementWindowStart) { this.settlementWindowStart = settlementWindowStart; return this; }
        public JuiceMarketSettlementBuilder settlementWindowEnd(LocalDateTime settlementWindowEnd) { this.settlementWindowEnd = settlementWindowEnd; return this; }
        public JuiceMarketSettlementBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public JuiceMarketSettlementBuilder status(String status) { this.status = status; return this; }
        public JuiceMarketSettlementBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public JuiceMarketSettlement build() {
            return new JuiceMarketSettlement(id, settlementWindowStart, settlementWindowEnd, idempotencyKey, status, createdAt);
        }
    }
}
