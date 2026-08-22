package com.retailpos.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_history")
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "old_price", nullable = false)
    private BigDecimal oldPrice;

    @Column(name = "new_price", nullable = false)
    private BigDecimal newPrice;

    @Column(name = "price_change")
    private BigDecimal priceChange;

    @Column(name = "demand_ratio")
    private Double demandRatio;

    @Column(name = "weighted_sales")
    private Double weightedSales;

    @Column(name = "target_sales")
    private Double targetSales;

    @Column(name = "calculation_window_start")
    private LocalDateTime calculationWindowStart;

    @Column(name = "calculation_window_end")
    private LocalDateTime calculationWindowEnd;

    @Column(name = "reason")
    private String reason;

    @Column(name = "raw_w0")
    private Integer rawW0 = 0;

    @Column(name = "raw_w1")
    private Integer rawW1 = 0;

    @Column(name = "raw_w2")
    private Integer rawW2 = 0;

    @Column(name = "unconsumed_w0")
    private Integer unconsumedW0 = 0;

    @Column(name = "trigger_type")
    private String triggerType = "SCHEDULED";

    @Column(name = "settlement_id")
    private String settlementId;

    @Column(name = "demand_score", nullable = false)
    private Double demandScore = 50.0;

    @Column(name = "stock_pressure_pct", nullable = false)
    private Double stockPressurePct = 0.0;

    @Column(name = "time_factor_multiplier", nullable = false)
    private Double timeFactorMultiplier = 1.0;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String explanation;

    @Column(name = "order_count")
    private Integer orderCount = 0;

    @Column(name = "raw_price_change_percent")
    private BigDecimal rawPriceChangePercent;

    @Column(name = "applied_price_change_percent")
    private BigDecimal appliedPriceChangePercent;

    @Column(name = "volatility")
    private BigDecimal volatility;

    @Column(name = "floor_price")
    private BigDecimal floorPrice;

    @Column(name = "ceiling_price")
    private BigDecimal ceilingPrice;

    @Column(name = "price_version")
    private Integer priceVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public PriceHistory() {}

    public PriceHistory(Long id, Long productId, BigDecimal oldPrice, BigDecimal newPrice, BigDecimal priceChange, Double demandRatio, Double weightedSales, Double targetSales, LocalDateTime calculationWindowStart, LocalDateTime calculationWindowEnd, String reason, Double demandScore, Double stockPressurePct, Double timeFactorMultiplier, String explanation, LocalDateTime createdAt) {
        this.id = id;
        this.productId = productId;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
        this.priceChange = priceChange != null ? priceChange : (oldPrice != null && newPrice != null ? newPrice.subtract(oldPrice) : BigDecimal.ZERO);
        this.demandRatio = demandRatio;
        this.weightedSales = weightedSales;
        this.targetSales = targetSales;
        this.calculationWindowStart = calculationWindowStart;
        this.calculationWindowEnd = calculationWindowEnd;
        this.reason = reason;
        this.demandScore = demandScore != null ? demandScore : 50.0;
        this.stockPressurePct = stockPressurePct != null ? stockPressurePct : 0.0;
        this.timeFactorMultiplier = timeFactorMultiplier != null ? timeFactorMultiplier : 1.0;
        this.explanation = explanation != null ? explanation : "PRICE_SETTLEMENT";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public BigDecimal getOldPrice() { return oldPrice; }
    public void setOldPrice(BigDecimal oldPrice) { this.oldPrice = oldPrice; }
    public BigDecimal getNewPrice() { return newPrice; }
    public void setNewPrice(BigDecimal newPrice) { this.newPrice = newPrice; }
    public BigDecimal getPriceChange() { return priceChange; }
    public void setPriceChange(BigDecimal priceChange) { this.priceChange = priceChange; }
    public Double getDemandRatio() { return demandRatio; }
    public void setDemandRatio(Double demandRatio) { this.demandRatio = demandRatio; }
    public Double getWeightedSales() { return weightedSales; }
    public void setWeightedSales(Double weightedSales) { this.weightedSales = weightedSales; }
    public Double getTargetSales() { return targetSales; }
    public void setTargetSales(Double targetSales) { this.targetSales = targetSales; }
    public LocalDateTime getCalculationWindowStart() { return calculationWindowStart; }
    public void setCalculationWindowStart(LocalDateTime calculationWindowStart) { this.calculationWindowStart = calculationWindowStart; }
    public LocalDateTime getCalculationWindowEnd() { return calculationWindowEnd; }
    public void setCalculationWindowEnd(LocalDateTime calculationWindowEnd) { this.calculationWindowEnd = calculationWindowEnd; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Double getDemandScore() { return demandScore; }
    public void setDemandScore(Double demandScore) { this.demandScore = demandScore; }
    public Double getStockPressurePct() { return stockPressurePct; }
    public void setStockPressurePct(Double stockPressurePct) { this.stockPressurePct = stockPressurePct; }
    public Double getTimeFactorMultiplier() { return timeFactorMultiplier; }
    public void setTimeFactorMultiplier(Double timeFactorMultiplier) { this.timeFactorMultiplier = timeFactorMultiplier; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getRawW0() { return rawW0; }
    public void setRawW0(Integer rawW0) { this.rawW0 = rawW0; }
    public Integer getRawW1() { return rawW1; }
    public void setRawW1(Integer rawW1) { this.rawW1 = rawW1; }
    public Integer getRawW2() { return rawW2; }
    public void setRawW2(Integer rawW2) { this.rawW2 = rawW2; }
    public Integer getUnconsumedW0() { return unconsumedW0; }
    public void setUnconsumedW0(Integer unconsumedW0) { this.unconsumedW0 = unconsumedW0; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getSettlementId() { return settlementId; }
    public void setSettlementId(String settlementId) { this.settlementId = settlementId; }

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public BigDecimal getRawPriceChangePercent() { return rawPriceChangePercent; }
    public void setRawPriceChangePercent(BigDecimal rawPriceChangePercent) { this.rawPriceChangePercent = rawPriceChangePercent; }
    public BigDecimal getAppliedPriceChangePercent() { return appliedPriceChangePercent; }
    public void setAppliedPriceChangePercent(BigDecimal appliedPriceChangePercent) { this.appliedPriceChangePercent = appliedPriceChangePercent; }
    public BigDecimal getVolatility() { return volatility; }
    public void setVolatility(BigDecimal volatility) { this.volatility = volatility; }
    public BigDecimal getFloorPrice() { return floorPrice; }
    public void setFloorPrice(BigDecimal floorPrice) { this.floorPrice = floorPrice; }
    public BigDecimal getCeilingPrice() { return ceilingPrice; }
    public void setCeilingPrice(BigDecimal ceilingPrice) { this.ceilingPrice = ceilingPrice; }
    public Integer getPriceVersion() { return priceVersion; }
    public void setPriceVersion(Integer priceVersion) { this.priceVersion = priceVersion; }

    public static PriceHistoryBuilder builder() { return new PriceHistoryBuilder(); }

    public static class PriceHistoryBuilder {
        private Long id;
        private Long productId;
        private BigDecimal oldPrice;
        private BigDecimal newPrice;
        private BigDecimal priceChange;
        private Double demandRatio;
        private Double weightedSales;
        private Double targetSales;
        private LocalDateTime calculationWindowStart;
        private LocalDateTime calculationWindowEnd;
        private String reason;
        private Integer rawW0 = 0;
        private Integer rawW1 = 0;
        private Integer rawW2 = 0;
        private Integer unconsumedW0 = 0;
        private String triggerType = "SCHEDULED";
        private String settlementId;
        private Double demandScore = 50.0;
        private Double stockPressurePct = 0.0;
        private Double timeFactorMultiplier = 1.0;
        private String explanation;
        private Integer orderCount = 0;
        private BigDecimal rawPriceChangePercent;
        private BigDecimal appliedPriceChangePercent;
        private BigDecimal volatility;
        private BigDecimal floorPrice;
        private BigDecimal ceilingPrice;
        private Integer priceVersion;
        private LocalDateTime createdAt = LocalDateTime.now();

        public PriceHistoryBuilder id(Long id) { this.id = id; return this; }
        public PriceHistoryBuilder productId(Long productId) { this.productId = productId; return this; }
        public PriceHistoryBuilder oldPrice(BigDecimal oldPrice) { this.oldPrice = oldPrice; return this; }
        public PriceHistoryBuilder newPrice(BigDecimal newPrice) { this.newPrice = newPrice; return this; }
        public PriceHistoryBuilder priceChange(BigDecimal priceChange) { this.priceChange = priceChange; return this; }
        public PriceHistoryBuilder demandRatio(Double demandRatio) { this.demandRatio = demandRatio; return this; }
        public PriceHistoryBuilder weightedSales(Double weightedSales) { this.weightedSales = weightedSales; return this; }
        public PriceHistoryBuilder targetSales(Double targetSales) { this.targetSales = targetSales; return this; }
        public PriceHistoryBuilder calculationWindowStart(LocalDateTime calculationWindowStart) { this.calculationWindowStart = calculationWindowStart; return this; }
        public PriceHistoryBuilder calculationWindowEnd(LocalDateTime calculationWindowEnd) { this.calculationWindowEnd = calculationWindowEnd; return this; }
        public PriceHistoryBuilder reason(String reason) { this.reason = reason; return this; }
        public PriceHistoryBuilder rawW0(Integer rawW0) { this.rawW0 = rawW0; return this; }
        public PriceHistoryBuilder rawW1(Integer rawW1) { this.rawW1 = rawW1; return this; }
        public PriceHistoryBuilder rawW2(Integer rawW2) { this.rawW2 = rawW2; return this; }
        public PriceHistoryBuilder unconsumedW0(Integer unconsumedW0) { this.unconsumedW0 = unconsumedW0; return this; }
        public PriceHistoryBuilder triggerType(String triggerType) { this.triggerType = triggerType; return this; }
        public PriceHistoryBuilder settlementId(String settlementId) { this.settlementId = settlementId; return this; }
        public PriceHistoryBuilder demandScore(Double demandScore) { this.demandScore = demandScore; return this; }
        public PriceHistoryBuilder stockPressurePct(Double stockPressurePct) { this.stockPressurePct = stockPressurePct; return this; }
        public PriceHistoryBuilder timeFactorMultiplier(Double timeFactorMultiplier) { this.timeFactorMultiplier = timeFactorMultiplier; return this; }
        public PriceHistoryBuilder explanation(String explanation) { this.explanation = explanation; return this; }
        public PriceHistoryBuilder orderCount(Integer orderCount) { this.orderCount = orderCount; return this; }
        public PriceHistoryBuilder rawPriceChangePercent(BigDecimal rawPriceChangePercent) { this.rawPriceChangePercent = rawPriceChangePercent; return this; }
        public PriceHistoryBuilder appliedPriceChangePercent(BigDecimal appliedPriceChangePercent) { this.appliedPriceChangePercent = appliedPriceChangePercent; return this; }
        public PriceHistoryBuilder volatility(BigDecimal volatility) { this.volatility = volatility; return this; }
        public PriceHistoryBuilder floorPrice(BigDecimal floorPrice) { this.floorPrice = floorPrice; return this; }
        public PriceHistoryBuilder ceilingPrice(BigDecimal ceilingPrice) { this.ceilingPrice = ceilingPrice; return this; }
        public PriceHistoryBuilder priceVersion(Integer priceVersion) { this.priceVersion = priceVersion; return this; }
        public PriceHistoryBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public PriceHistory build() {
            PriceHistory ph = new PriceHistory(id, productId, oldPrice, newPrice, priceChange, demandRatio, weightedSales, targetSales, calculationWindowStart, calculationWindowEnd, reason, demandScore, stockPressurePct, timeFactorMultiplier, explanation, createdAt);
            ph.setRawW0(rawW0);
            ph.setRawW1(rawW1);
            ph.setRawW2(rawW2);
            ph.setUnconsumedW0(unconsumedW0);
            ph.setTriggerType(triggerType);
            ph.setSettlementId(settlementId);
            ph.setOrderCount(orderCount);
            ph.setRawPriceChangePercent(rawPriceChangePercent);
            ph.setAppliedPriceChangePercent(appliedPriceChangePercent);
            ph.setVolatility(volatility);
            ph.setFloorPrice(floorPrice);
            ph.setCeilingPrice(ceilingPrice);
            ph.setPriceVersion(priceVersion);
            return ph;
        }
    }
}
