package com.retailpos.pricing.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PriceQuote {

    private String quoteId;
    private Long productId;
    private String productName;
    private int quantity;
    private BigDecimal lockedPrice;
    private int priceVersion;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean redeemed;

    public PriceQuote() {}

    public PriceQuote(String quoteId, Long productId, String productName, int quantity, BigDecimal lockedPrice, int priceVersion, LocalDateTime createdAt, LocalDateTime expiresAt, boolean redeemed) {
        this.quoteId = quoteId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.lockedPrice = lockedPrice;
        this.priceVersion = priceVersion;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.redeemed = redeemed;
    }

    public String getQuoteId() { return quoteId; }
    public void setQuoteId(String quoteId) { this.quoteId = quoteId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getLockedPrice() { return lockedPrice; }
    public void setLockedPrice(BigDecimal lockedPrice) { this.lockedPrice = lockedPrice; }

    public int getPriceVersion() { return priceVersion; }
    public void setPriceVersion(int priceVersion) { this.priceVersion = priceVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isRedeemed() { return redeemed; }
    public void setRedeemed(boolean redeemed) { this.redeemed = redeemed; }

    public static PriceQuoteBuilder builder() { return new PriceQuoteBuilder(); }

    public static class PriceQuoteBuilder {
        private String quoteId;
        private Long productId;
        private String productName;
        private int quantity;
        private BigDecimal lockedPrice;
        private int priceVersion;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private boolean redeemed;

        public PriceQuoteBuilder quoteId(String quoteId) { this.quoteId = quoteId; return this; }
        public PriceQuoteBuilder productId(Long productId) { this.productId = productId; return this; }
        public PriceQuoteBuilder productName(String productName) { this.productName = productName; return this; }
        public PriceQuoteBuilder quantity(int quantity) { this.quantity = quantity; return this; }
        public PriceQuoteBuilder lockedPrice(BigDecimal lockedPrice) { this.lockedPrice = lockedPrice; return this; }
        public PriceQuoteBuilder priceVersion(int priceVersion) { this.priceVersion = priceVersion; return this; }
        public PriceQuoteBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PriceQuoteBuilder expiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; return this; }
        public PriceQuoteBuilder redeemed(boolean redeemed) { this.redeemed = redeemed; return this; }

        public PriceQuote build() {
            return new PriceQuote(quoteId, productId, productName, quantity, lockedPrice, priceVersion, createdAt, expiresAt, redeemed);
        }
    }
}
