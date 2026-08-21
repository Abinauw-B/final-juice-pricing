package com.retailpos.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_orders")
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod; // CASH, UPI, CARD

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus = "COMPLETED";

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "subtotal")
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesOrderItem> items = new ArrayList<>();

    public SalesOrder() {}

    public SalesOrder(Long id, String orderNumber, BigDecimal totalAmount, String paymentMethod, String paymentStatus, String idempotencyKey, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount, LocalDateTime createdAt, List<SalesOrderItem> items) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus != null ? paymentStatus : "COMPLETED";
        this.idempotencyKey = idempotencyKey;
        this.subtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.items = items != null ? items : new ArrayList<>();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<SalesOrderItem> getItems() { return items; }
    public void setItems(List<SalesOrderItem> items) { this.items = items; }

    public static SalesOrderBuilder builder() { return new SalesOrderBuilder(); }

    public static class SalesOrderBuilder {
        private Long id;
        private String orderNumber;
        private BigDecimal totalAmount;
        private String paymentMethod;
        private String paymentStatus = "COMPLETED";
        private String idempotencyKey;
        private BigDecimal subtotal = BigDecimal.ZERO;
        private BigDecimal discountAmount = BigDecimal.ZERO;
        private BigDecimal taxAmount = BigDecimal.ZERO;
        private LocalDateTime createdAt = LocalDateTime.now();
        private List<SalesOrderItem> items = new ArrayList<>();

        public SalesOrderBuilder id(Long id) { this.id = id; return this; }
        public SalesOrderBuilder orderNumber(String orderNumber) { this.orderNumber = orderNumber; return this; }
        public SalesOrderBuilder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
        public SalesOrderBuilder paymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; return this; }
        public SalesOrderBuilder paymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; return this; }
        public SalesOrderBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public SalesOrderBuilder subtotal(BigDecimal subtotal) { this.subtotal = subtotal; return this; }
        public SalesOrderBuilder discountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; return this; }
        public SalesOrderBuilder taxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; return this; }
        public SalesOrderBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public SalesOrderBuilder items(List<SalesOrderItem> items) { this.items = items; return this; }

        public SalesOrder build() {
            return new SalesOrder(id, orderNumber, totalAmount, paymentMethod, paymentStatus, idempotencyKey, subtotal, discountAmount, taxAmount, createdAt, items);
        }
    }
}
