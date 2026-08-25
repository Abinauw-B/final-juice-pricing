package com.retailpos.pricing.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PurchaseEvent {

    private Long orderId;
    private String orderNumber;
    private Long productId;
    private String productName;
    private int quantity;
    private BigDecimal executedPrice;
    private LocalDateTime timestamp;

    public PurchaseEvent() {}

    public PurchaseEvent(Long orderId, String orderNumber, Long productId, String productName, int quantity, BigDecimal executedPrice, LocalDateTime timestamp) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.executedPrice = executedPrice;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getExecutedPrice() { return executedPrice; }
    public void setExecutedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public static PurchaseEventBuilder builder() { return new PurchaseEventBuilder(); }

    public static class PurchaseEventBuilder {
        private Long orderId;
        private String orderNumber;
        private Long productId;
        private String productName;
        private int quantity;
        private BigDecimal executedPrice;
        private LocalDateTime timestamp;

        public PurchaseEventBuilder orderId(Long orderId) { this.orderId = orderId; return this; }
        public PurchaseEventBuilder orderNumber(String orderNumber) { this.orderNumber = orderNumber; return this; }
        public PurchaseEventBuilder productId(Long productId) { this.productId = productId; return this; }
        public PurchaseEventBuilder productName(String productName) { this.productName = productName; return this; }
        public PurchaseEventBuilder quantity(int quantity) { this.quantity = quantity; return this; }
        public PurchaseEventBuilder executedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; return this; }
        public PurchaseEventBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }

        public PurchaseEvent build() {
            return new PurchaseEvent(orderId, orderNumber, productId, productName, quantity, executedPrice, timestamp);
        }
    }
}
