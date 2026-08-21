package com.retailpos.pos;

import com.retailpos.domain.*;
import com.retailpos.inventory.JuiceBatchService;
import com.retailpos.pricing.MarketCrashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
public class POSService {

    private static final Logger log = LoggerFactory.getLogger(POSService.class);

    private final ProductRepository productRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final JuiceBatchService juiceBatchService;
    private final MarketCrashService marketCrashService;
    private final com.retailpos.pricing.PriceAdjustmentService priceAdjustmentService;
    private final com.retailpos.pricing.PriceLockService priceLockService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate transactionTemplate;

    public POSService(ProductRepository productRepository, SalesOrderRepository salesOrderRepository, PriceHistoryRepository priceHistoryRepository, JuiceBatchService juiceBatchService, MarketCrashService marketCrashService, com.retailpos.pricing.PriceAdjustmentService priceAdjustmentService, com.retailpos.pricing.PriceLockService priceLockService, SimpMessagingTemplate messagingTemplate, PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.juiceBatchService = juiceBatchService;
        this.marketCrashService = marketCrashService;
        this.priceAdjustmentService = priceAdjustmentService;
        this.priceLockService = priceLockService;
        this.messagingTemplate = messagingTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public static class CartItemRequest {
        private Long productId;
        private Integer quantity;
        private Integer cupSizeMl;
        private Integer priceVersion;
        private BigDecimal lockedPrice;
        private String priceLockToken;

        public CartItemRequest() {}
        public CartItemRequest(Long productId, Integer quantity, Integer cupSizeMl) {
            this.productId = productId;
            this.quantity = quantity;
            this.cupSizeMl = cupSizeMl;
        }
        public CartItemRequest(Long productId, Integer quantity, Integer cupSizeMl, Integer priceVersion, BigDecimal lockedPrice, String priceLockToken) {
            this.productId = productId;
            this.quantity = quantity;
            this.cupSizeMl = cupSizeMl;
            this.priceVersion = priceVersion;
            this.lockedPrice = lockedPrice;
            this.priceLockToken = priceLockToken;
        }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Integer getCupSizeMl() { return cupSizeMl; }
        public void setCupSizeMl(Integer cupSizeMl) { this.cupSizeMl = cupSizeMl; }
        public Integer getPriceVersion() { return priceVersion; }
        public void setPriceVersion(Integer priceVersion) { this.priceVersion = priceVersion; }
        public BigDecimal getLockedPrice() { return lockedPrice; }
        public void setLockedPrice(BigDecimal lockedPrice) { this.lockedPrice = lockedPrice; }
        public String getPriceLockToken() { return priceLockToken; }
        public void setPriceLockToken(String priceLockToken) { this.priceLockToken = priceLockToken; }
    }

    public static class CheckoutRequest {
        private List<CartItemRequest> items;
        private String paymentMethod;
        private String idempotencyKey;
        private BigDecimal discountAmount;
        private BigDecimal taxAmount;

        public CheckoutRequest() {}
        public CheckoutRequest(List<CartItemRequest> items, String paymentMethod) {
            this.items = items;
            this.paymentMethod = paymentMethod;
        }
        public CheckoutRequest(List<CartItemRequest> items, String paymentMethod, String idempotencyKey, BigDecimal discountAmount, BigDecimal taxAmount) {
            this.items = items;
            this.paymentMethod = paymentMethod;
            this.idempotencyKey = idempotencyKey;
            this.discountAmount = discountAmount;
            this.taxAmount = taxAmount;
        }
        public List<CartItemRequest> getItems() { return items; }
        public void setItems(List<CartItemRequest> items) { this.items = items; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
        public BigDecimal getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    }

    public static class CheckoutResponse {
        private boolean success = true;
        private Long orderId;
        private String orderNumber;
        private String message = "POS order completed successfully";
        private BigDecimal totalAmount;
        private String paymentMethod;
        private String paymentStatus;
        private LocalDateTime timestamp;
        private List<OrderItemResponse> items;

        public CheckoutResponse() {}
        public CheckoutResponse(boolean success, Long orderId, String orderNumber, String message, BigDecimal totalAmount, String paymentMethod, String paymentStatus, LocalDateTime timestamp, List<OrderItemResponse> items) {
            this.success = success;
            this.orderId = orderId;
            this.orderNumber = orderNumber;
            this.message = message;
            this.totalAmount = totalAmount;
            this.paymentMethod = paymentMethod;
            this.paymentStatus = paymentStatus;
            this.timestamp = timestamp;
            this.items = items;
        }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getOrderNumber() { return orderNumber; }
        public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
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
            private boolean success = true;
            private Long orderId;
            private String orderNumber;
            private String message = "POS order completed successfully";
            private BigDecimal totalAmount;
            private String paymentMethod;
            private String paymentStatus;
            private LocalDateTime timestamp;
            private List<OrderItemResponse> items;

            public CheckoutResponseBuilder success(boolean success) { this.success = success; return this; }
            public CheckoutResponseBuilder orderId(Long orderId) { this.orderId = orderId; return this; }
            public CheckoutResponseBuilder orderNumber(String orderNumber) { this.orderNumber = orderNumber; return this; }
            public CheckoutResponseBuilder message(String message) { this.message = message; return this; }
            public CheckoutResponseBuilder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
            public CheckoutResponseBuilder paymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; return this; }
            public CheckoutResponseBuilder paymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; return this; }
            public CheckoutResponseBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
            public CheckoutResponseBuilder items(List<OrderItemResponse> items) { this.items = items; return this; }
            public CheckoutResponse build() { return new CheckoutResponse(success, orderId, orderNumber, message, totalAmount, paymentMethod, paymentStatus, timestamp, items); }
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

    private final Map<String, java.util.concurrent.CompletableFuture<CheckoutResponse>> pendingIdempotencyRequests = new java.util.concurrent.ConcurrentHashMap<>();

    public CheckoutResponse processCheckout(CheckoutRequest request) {
        log.info("POS checkout request received with {} items", request != null && request.getItems() != null ? request.getItems().size() : 0);

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            log.error("POS checkout failed: Cart cannot be empty");
            throw new IllegalArgumentException("Cart cannot be empty for checkout");
        }

        // Idempotency check: Return existing order if idempotency key already processed or in progress
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            String key = request.getIdempotencyKey();
            Optional<SalesOrder> existingOrder = salesOrderRepository.findByIdempotencyKey(key);
            if (existingOrder.isPresent()) {
                SalesOrder existing = existingOrder.get();
                log.info("Idempotent checkout request detected for key {}. Returning existing order #{}", key, existing.getOrderNumber());
                List<OrderItemResponse> existingItems = existing.getItems().stream().map(i ->
                        OrderItemResponse.builder()
                                .productName(i.getProductName())
                                .quantity(i.getQuantity())
                                .cupSizeMl(i.getCupSizeMl())
                                .unitPrice(i.getUnitPrice())
                                .totalPrice(i.getTotalPrice())
                                .volumeDeductedMl(i.getVolumeDeductedMl())
                                .build()
                ).toList();

                return CheckoutResponse.builder()
                        .success(true)
                        .orderId(existing.getId())
                        .orderNumber(existing.getOrderNumber())
                        .message("Order processed successfully (idempotent duplicate)")
                        .totalAmount(existing.getTotalAmount())
                        .paymentMethod(existing.getPaymentMethod())
                        .paymentStatus(existing.getPaymentStatus())
                        .timestamp(existing.getCreatedAt())
                        .items(existingItems)
                        .build();
            }

            java.util.concurrent.CompletableFuture<CheckoutResponse> future = new java.util.concurrent.CompletableFuture<>();
            java.util.concurrent.CompletableFuture<CheckoutResponse> previous = pendingIdempotencyRequests.putIfAbsent(key, future);
            if (previous != null) {
                try {
                    return previous.get(15, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Error waiting for concurrent idempotent checkout for key {}", key, e);
                    Optional<SalesOrder> retryOrder = salesOrderRepository.findByIdempotencyKey(key);
                    if (retryOrder.isPresent()) {
                        SalesOrder existing = retryOrder.get();
                        List<OrderItemResponse> existingItems = existing.getItems().stream().map(i ->
                                OrderItemResponse.builder()
                                        .productName(i.getProductName())
                                        .quantity(i.getQuantity())
                                        .cupSizeMl(i.getCupSizeMl())
                                        .unitPrice(i.getUnitPrice())
                                        .totalPrice(i.getTotalPrice())
                                        .volumeDeductedMl(i.getVolumeDeductedMl())
                                        .build()
                        ).toList();

                        return CheckoutResponse.builder()
                                .success(true)
                                .orderId(existing.getId())
                                .orderNumber(existing.getOrderNumber())
                                .message("Order processed successfully (idempotent duplicate)")
                                .totalAmount(existing.getTotalAmount())
                                .paymentMethod(existing.getPaymentMethod())
                                .paymentStatus(existing.getPaymentStatus())
                                .timestamp(existing.getCreatedAt())
                                .items(existingItems)
                                .build();
                    }
                }
            }

            try {
                CheckoutResponse res = transactionTemplate.execute(status -> doProcessCheckout(request));
                future.complete(res);
                return res;
            } catch (Exception ex) {
                future.completeExceptionally(ex);
                throw ex;
            } finally {
                pendingIdempotencyRequests.remove(key);
            }
        }

        return transactionTemplate.execute(status -> doProcessCheckout(request));
    }

    private CheckoutResponse doProcessCheckout(CheckoutRequest request) {

        String orderNum = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String paymentMethod = (request.getPaymentMethod() != null) ? request.getPaymentMethod().toUpperCase() : "CASH";

        BigDecimal subtotal = BigDecimal.ZERO;
        List<SalesOrderItem> orderItems = new ArrayList<>();
        List<OrderItemResponse> itemResponses = new ArrayList<>();

        SalesOrder salesOrder = SalesOrder.builder()
                .orderNumber(orderNum)
                .idempotencyKey(request.getIdempotencyKey())
                .totalAmount(BigDecimal.ZERO)
                .paymentMethod(paymentMethod)
                .paymentStatus("COMPLETED")
                .createdAt(LocalDateTime.now())
                .build();

        Set<Long> purchasedProductIds = new HashSet<>();

        for (CartItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + itemReq.getProductId()));

            log.info("Product validation successful: ID={}, Name={}", product.getId(), product.getName());

            purchasedProductIds.add(product.getId());

            int cupSize = (itemReq.getCupSizeMl() != null && itemReq.getCupSizeMl() > 0) ? itemReq.getCupSizeMl() : product.getDefaultCupSizeMl();
            int qty = (itemReq.getQuantity() != null && itemReq.getQuantity() > 0) ? itemReq.getQuantity() : 1;
            int totalVolumeMl = cupSize * qty;

            // 100% Server Authoritative Price Determination
            BigDecimal effectivePrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : product.getDefaultCupPrice();
            int effectiveVersion = product.getPriceVersion() != null ? product.getPriceVersion() : 1;

            if (itemReq.getPriceLockToken() != null && !itemReq.getPriceLockToken().isBlank() && priceLockService != null) {
                com.retailpos.pricing.PriceLockService.LockedPriceVersion lock = priceLockService.validateAndRedeemLock(itemReq.getPriceLockToken());
                effectivePrice = lock.getLockedPrice();
                effectiveVersion = lock.getPriceVersion();
            } else if (itemReq.getPriceVersion() != null && product.getPriceVersion() != null) {
                if (!itemReq.getPriceVersion().equals(product.getPriceVersion())) {
                    throw new IllegalStateException("PRICE_CHANGED: Price version mismatch for '" + product.getName() + "'. Server current price is ₹" + effectivePrice + ". Please refresh cart.");
                }
            }

            BigDecimal itemTotal = effectivePrice.multiply(BigDecimal.valueOf(qty));
            subtotal = subtotal.add(itemTotal);

            // Atomically deduct volume from active batch using pessimistic lock
            JuiceBatch updatedBatch = juiceBatchService.deductBatchVolume(product.getId(), totalVolumeMl);
            log.info("Inventory updated: ProductId={}, Deducted={}ml, Remaining={}ml", product.getId(), totalVolumeMl, updatedBatch.getRemainingVolumeMl());

            SalesOrderItem orderItem = SalesOrderItem.builder()
                    .salesOrder(salesOrder)
                    .productId(product.getId())
                    .productName(product.getName())
                    .cupSizeMl(cupSize)
                    .unitPrice(effectivePrice)
                    .lockedPrice(effectivePrice)
                    .priceVersion(effectiveVersion)
                    .quantity(qty)
                    .totalPrice(itemTotal)
                    .volumeDeductedMl(totalVolumeMl)
                    .build();

            orderItems.add(orderItem);

            itemResponses.add(OrderItemResponse.builder()
                    .productName(product.getName())
                    .quantity(qty)
                    .cupSizeMl(cupSize)
                    .unitPrice(effectivePrice)
                    .totalPrice(itemTotal)
                    .volumeDeductedMl(totalVolumeMl)
                    .build());
        }

        BigDecimal discount = (request.getDiscountAmount() != null) ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal tax = (request.getTaxAmount() != null) ? request.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal orderTotal = subtotal.subtract(discount).add(tax);

        salesOrder.setSubtotal(subtotal);
        salesOrder.setDiscountAmount(discount);
        salesOrder.setTaxAmount(tax);
        salesOrder.setTotalAmount(orderTotal);
        salesOrder.setItems(orderItems);

        SalesOrder savedOrder;
        try {
            savedOrder = salesOrderRepository.save(salesOrder);
            log.info("Order created and items persisted: OrderID={}, OrderNumber={}", savedOrder.getId(), savedOrder.getOrderNumber());
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
                Optional<SalesOrder> existingOrder = salesOrderRepository.findByIdempotencyKey(request.getIdempotencyKey());
                if (existingOrder.isPresent()) {
                    SalesOrder existing = existingOrder.get();
                    log.info("Concurrent idempotent duplicate request caught for key {}. Returning existing order #{}", request.getIdempotencyKey(), existing.getOrderNumber());
                    List<OrderItemResponse> existingItems = existing.getItems().stream().map(i ->
                            OrderItemResponse.builder()
                                    .productName(i.getProductName())
                                    .quantity(i.getQuantity())
                                    .cupSizeMl(i.getCupSizeMl())
                                    .unitPrice(i.getUnitPrice())
                                    .totalPrice(i.getTotalPrice())
                                    .volumeDeductedMl(i.getVolumeDeductedMl())
                                    .build()
                    ).toList();

                    return CheckoutResponse.builder()
                            .success(true)
                            .orderId(existing.getId())
                            .orderNumber(existing.getOrderNumber())
                            .message("Order processed successfully (idempotent duplicate)")
                            .totalAmount(existing.getTotalAmount())
                            .paymentMethod(existing.getPaymentMethod())
                            .paymentStatus(existing.getPaymentStatus())
                            .timestamp(existing.getCreatedAt())
                            .items(existingItems)
                            .build();
                }
            }
            throw dive;
        }

        // Authoritative Bar Stock Exchange dynamic price recalculation across all products
        if (marketCrashService == null || !marketCrashService.isCrashActive()) {
            try {
                priceAdjustmentService.evaluateAllProducts();
                log.info("Pricing recalculated for order {}", savedOrder.getOrderNumber());
                if (messagingTemplate != null) {
                    messagingTemplate.convertAndSend("/topic/prices", productRepository.findAll());
                    log.info("Price broadcast to /topic/prices");
                }
            } catch (Exception e) {
                log.warn("Non-fatal dynamic pricing evaluation error during checkout: {}", e.getMessage());
            }
        }

        log.info("POS checkout completed successfully for Order #{}", savedOrder.getOrderNumber());

        return CheckoutResponse.builder()
                .success(true)
                .orderId(savedOrder.getId())
                .orderNumber(savedOrder.getOrderNumber())
                .message("POS order completed successfully")
                .totalAmount(orderTotal)
                .paymentMethod(paymentMethod)
                .paymentStatus("COMPLETED")
                .timestamp(LocalDateTime.now())
                .items(itemResponses)
                .build();
    }
}


