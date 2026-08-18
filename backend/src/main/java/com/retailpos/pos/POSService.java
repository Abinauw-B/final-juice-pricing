package com.retailpos.pos;

import com.retailpos.domain.*;
import com.retailpos.inventory.JuiceBatchService;
import com.retailpos.pricing.MarketCrashService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
public class POSService {

    private final ProductRepository productRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final JuiceBatchService juiceBatchService;
    private final MarketCrashService marketCrashService;
    private final SimpMessagingTemplate messagingTemplate;

    public POSService(ProductRepository productRepository, SalesOrderRepository salesOrderRepository, PriceHistoryRepository priceHistoryRepository, JuiceBatchService juiceBatchService, MarketCrashService marketCrashService, SimpMessagingTemplate messagingTemplate) {
        this.productRepository = productRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.juiceBatchService = juiceBatchService;
        this.marketCrashService = marketCrashService;
        this.messagingTemplate = messagingTemplate;
    }

    public static class CartItemRequest {
        private Long productId;
        private Integer quantity;
        private Integer cupSizeMl;

        public CartItemRequest() {}
        public CartItemRequest(Long productId, Integer quantity, Integer cupSizeMl) {
            this.productId = productId;
            this.quantity = quantity;
            this.cupSizeMl = cupSizeMl;
        }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Integer getCupSizeMl() { return cupSizeMl; }
        public void setCupSizeMl(Integer cupSizeMl) { this.cupSizeMl = cupSizeMl; }
    }

    public static class CheckoutRequest {
        private List<CartItemRequest> items;
        private String paymentMethod;

        public CheckoutRequest() {}
        public CheckoutRequest(List<CartItemRequest> items, String paymentMethod) {
            this.items = items;
            this.paymentMethod = paymentMethod;
        }
        public List<CartItemRequest> getItems() { return items; }
        public void setItems(List<CartItemRequest> items) { this.items = items; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    }

    public static class CheckoutResponse {
        private String orderNumber;
        private BigDecimal totalAmount;
        private String paymentMethod;
        private String paymentStatus;
        private LocalDateTime timestamp;
        private List<OrderItemResponse> items;

        public CheckoutResponse() {}
        public CheckoutResponse(String orderNumber, BigDecimal totalAmount, String paymentMethod, String paymentStatus, LocalDateTime timestamp, List<OrderItemResponse> items) {
            this.orderNumber = orderNumber;
            this.totalAmount = totalAmount;
            this.paymentMethod = paymentMethod;
            this.paymentStatus = paymentStatus;
            this.timestamp = timestamp;
            this.items = items;
        }
        public String getOrderNumber() { return orderNumber; }
        public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public List<OrderItemResponse> getItems() { return items; }
        public void setItems(List<OrderItemResponse> items) { this.items = items; }

        public static CheckoutResponseBuilder builder() { return new CheckoutResponseBuilder(); }
        public static class CheckoutResponseBuilder {
            private String orderNumber;
            private BigDecimal totalAmount;
            private String paymentMethod;
            private String paymentStatus;
            private LocalDateTime timestamp;
            private List<OrderItemResponse> items;

            public CheckoutResponseBuilder orderNumber(String orderNumber) { this.orderNumber = orderNumber; return this; }
            public CheckoutResponseBuilder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
            public CheckoutResponseBuilder paymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; return this; }
            public CheckoutResponseBuilder paymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; return this; }
            public CheckoutResponseBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
            public CheckoutResponseBuilder items(List<OrderItemResponse> items) { this.items = items; return this; }
            public CheckoutResponse build() { return new CheckoutResponse(orderNumber, totalAmount, paymentMethod, paymentStatus, timestamp, items); }
        }
    }

    public static class OrderItemResponse {
        private String productName;
        private Integer quantity;
        private Integer cupSizeMl;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private Integer volumeDeductedMl;

        public OrderItemResponse() {}
        public OrderItemResponse(String productName, Integer quantity, Integer cupSizeMl, BigDecimal unitPrice, BigDecimal totalPrice, Integer volumeDeductedMl) {
            this.productName = productName;
            this.quantity = quantity;
            this.cupSizeMl = cupSizeMl;
            this.unitPrice = unitPrice;
            this.totalPrice = totalPrice;
            this.volumeDeductedMl = volumeDeductedMl;
        }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Integer getCupSizeMl() { return cupSizeMl; }
        public void setCupSizeMl(Integer cupSizeMl) { this.cupSizeMl = cupSizeMl; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getTotalPrice() { return totalPrice; }
        public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
        public Integer getVolumeDeductedMl() { return volumeDeductedMl; }
        public void setVolumeDeductedMl(Integer volumeDeductedMl) { this.volumeDeductedMl = volumeDeductedMl; }

        public static OrderItemResponseBuilder builder() { return new OrderItemResponseBuilder(); }
        public static class OrderItemResponseBuilder {
            private String productName;
            private Integer quantity;
            private Integer cupSizeMl;
            private BigDecimal unitPrice;
            private BigDecimal totalPrice;
            private Integer volumeDeductedMl;

            public OrderItemResponseBuilder productName(String productName) { this.productName = productName; return this; }
            public OrderItemResponseBuilder quantity(Integer quantity) { this.quantity = quantity; return this; }
            public OrderItemResponseBuilder cupSizeMl(Integer cupSizeMl) { this.cupSizeMl = cupSizeMl; return this; }
            public OrderItemResponseBuilder unitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; return this; }
            public OrderItemResponseBuilder totalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; return this; }
            public OrderItemResponseBuilder volumeDeductedMl(Integer volumeDeductedMl) { this.volumeDeductedMl = volumeDeductedMl; return this; }
            public OrderItemResponse build() { return new OrderItemResponse(productName, quantity, cupSizeMl, unitPrice, totalPrice, volumeDeductedMl); }
        }
    }

    @Transactional
    public CheckoutResponse processCheckout(CheckoutRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart cannot be empty for checkout");
        }

        String orderNum = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String paymentMethod = (request.getPaymentMethod() != null) ? request.getPaymentMethod().toUpperCase() : "CASH";

        BigDecimal orderTotal = BigDecimal.ZERO;
        List<SalesOrderItem> orderItems = new ArrayList<>();
        List<OrderItemResponse> itemResponses = new ArrayList<>();

        SalesOrder salesOrder = SalesOrder.builder()
                .orderNumber(orderNum)
                .totalAmount(BigDecimal.ZERO)
                .paymentMethod(paymentMethod)
                .paymentStatus("COMPLETED")
                .createdAt(LocalDateTime.now())
                .build();

        Set<Long> purchasedProductIds = new HashSet<>();

        for (CartItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + itemReq.getProductId()));

            purchasedProductIds.add(product.getId());

            int cupSize = (itemReq.getCupSizeMl() != null && itemReq.getCupSizeMl() > 0) ? itemReq.getCupSizeMl() : product.getDefaultCupSizeMl();
            int qty = (itemReq.getQuantity() != null && itemReq.getQuantity() > 0) ? itemReq.getQuantity() : 1;

            int totalVolumeMl = cupSize * qty;

            // Atomically deduct volume from active batch using pessimistic lock
            JuiceBatch updatedBatch = juiceBatchService.deductBatchVolume(product.getId(), totalVolumeMl);

            BigDecimal unitPrice = product.getCurrentCupPrice();
            BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            orderTotal = orderTotal.add(itemTotal);

            SalesOrderItem orderItem = SalesOrderItem.builder()
                    .salesOrder(salesOrder)
                    .productId(product.getId())
                    .productName(product.getName())
                    .cupSizeMl(cupSize)
                    .unitPrice(unitPrice)
                    .quantity(qty)
                    .totalPrice(itemTotal)
                    .volumeDeductedMl(totalVolumeMl)
                    .build();

            orderItems.add(orderItem);

            itemResponses.add(OrderItemResponse.builder()
                    .productName(product.getName())
                    .quantity(qty)
                    .cupSizeMl(cupSize)
                    .unitPrice(unitPrice)
                    .totalPrice(itemTotal)
                    .volumeDeductedMl(totalVolumeMl)
                    .build());
        }

        salesOrder.setTotalAmount(orderTotal);
        salesOrder.setItems(orderItems);
        salesOrderRepository.save(salesOrder);

        // Bar Stock Exchange dynamic price recalculation across all products
        if (marketCrashService == null || !marketCrashService.isCrashActive()) {
            List<Product> allProducts = productRepository.findAll();
            LocalDateTime now = LocalDateTime.now();

            int totalPurchasedQty = request.getItems().stream().mapToInt(CartItemRequest::getQuantity).sum();

            for (Product p : allProducts) {
                BigDecimal oldPrice = p.getCurrentCupPrice();
                BigDecimal newPrice = oldPrice;
                String explanation;

                if (purchasedProductIds.contains(p.getId())) {
                    // Purchased item -> Price surges strictly by +₹1 step (e.g. ₹18 -> ₹19 -> ₹20)
                    int surge = 1;
                    newPrice = oldPrice.add(BigDecimal.valueOf(surge));
                    if (newPrice.compareTo(p.getMaxCupPrice()) > 0) {
                        newPrice = p.getMaxCupPrice();
                    }
                    explanation = String.format("📈 BAR STOCK SURGE: Buying volume surge (+%d cup(s)). Price increased from ₹%s to ₹%s for %s.", totalPurchasedQty, oldPrice, newPrice, p.getFlavour());
                } else {
                    // Unpurchased items -> Dynamic market variation & capital shift discount (-1)
                    if (oldPrice.compareTo(p.getMinCupPrice()) > 0) {
                        newPrice = oldPrice.subtract(BigDecimal.ONE);
                        explanation = String.format("📉 BAR STOCK DIVERTS: Demand shifted away. Price discounted from ₹%s to ₹%s for %s.", oldPrice, newPrice, p.getFlavour());
                    } else {
                        explanation = String.format("Price for %s locked at absolute floor boundary ₹%s.", p.getFlavour(), p.getMinCupPrice());
                    }
                }

                if (newPrice.compareTo(oldPrice) != 0) {
                    p.setCurrentCupPrice(newPrice);
                    p.setLastPriceChangeTimestamp(now);
                    productRepository.saveAndFlush(p);

                    PriceHistory history = PriceHistory.builder()
                            .productId(p.getId())
                            .oldPrice(oldPrice)
                            .newPrice(newPrice)
                            .demandScore(purchasedProductIds.contains(p.getId()) ? 92.0 : 28.0)
                            .stockPressurePct(50.0)
                            .timeFactorMultiplier(1.0)
                            .explanation(explanation)
                            .createdAt(now)
                            .build();
                    priceHistoryRepository.save(history);
                }
            }
            productRepository.flush();
            try {
                if (messagingTemplate != null) {
                    messagingTemplate.convertAndSend("/topic/prices", productRepository.findAll());
                }
            } catch (Exception e) {}
        }

        return CheckoutResponse.builder()
                .orderNumber(orderNum)
                .totalAmount(orderTotal)
                .paymentMethod(paymentMethod)
                .paymentStatus("COMPLETED")
                .timestamp(LocalDateTime.now())
                .items(itemResponses)
                .build();
    }
}

