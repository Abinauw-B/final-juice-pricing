package com.retailpos.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_events")
public class MarketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity")
    private Integer quantity = 1;

    @Column(name = "price_before")
    private BigDecimal priceBefore;

    @Column(name = "price_after")
    private BigDecimal priceAfter;

    @Column(name = "market_version", nullable = false)
    private Integer marketVersion;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public MarketEvent() {}

    public MarketEvent(Long id, String eventType, Long productId, Integer quantity, BigDecimal priceBefore, BigDecimal priceAfter, Integer marketVersion, String details, LocalDateTime createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.productId = productId;
        this.quantity = quantity != null ? quantity : 1;
        this.priceBefore = priceBefore;
        this.priceAfter = priceAfter;
        this.marketVersion = marketVersion != null ? marketVersion : 1;
        this.details = details;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getPriceBefore() { return priceBefore; }
    public void setPriceBefore(BigDecimal priceBefore) { this.priceBefore = priceBefore; }

    public BigDecimal getPriceAfter() { return priceAfter; }
    public void setPriceAfter(BigDecimal priceAfter) { this.priceAfter = priceAfter; }

    public Integer getMarketVersion() { return marketVersion; }
    public void setMarketVersion(Integer marketVersion) { this.marketVersion = marketVersion; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static MarketEventBuilder builder() { return new MarketEventBuilder(); }

    public static class MarketEventBuilder {
        private Long id;
        private String eventType;
        private Long productId;
        private Integer quantity = 1;
        private BigDecimal priceBefore;
        private BigDecimal priceAfter;
        private Integer marketVersion = 1;
        private String details;
        private LocalDateTime createdAt;

        public MarketEventBuilder id(Long id) { this.id = id; return this; }
        public MarketEventBuilder eventType(String eventType) { this.eventType = eventType; return this; }
        public MarketEventBuilder productId(Long productId) { this.productId = productId; return this; }
        public MarketEventBuilder quantity(Integer quantity) { this.quantity = quantity; return this; }
        public MarketEventBuilder priceBefore(BigDecimal priceBefore) { this.priceBefore = priceBefore; return this; }
        public MarketEventBuilder priceAfter(BigDecimal priceAfter) { this.priceAfter = priceAfter; return this; }
        public MarketEventBuilder marketVersion(Integer marketVersion) { this.marketVersion = marketVersion; return this; }
        public MarketEventBuilder details(String details) { this.details = details; return this; }
        public MarketEventBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public MarketEvent build() {
            return new MarketEvent(id, eventType, productId, quantity, priceBefore, priceAfter, marketVersion, details, createdAt);
        }
    }
}
