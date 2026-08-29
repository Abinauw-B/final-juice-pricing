package com.retailpos.pos;

import com.retailpos.domain.PriceHistory;
import com.retailpos.domain.PriceHistoryRepository;
import com.retailpos.domain.Product;
import com.retailpos.domain.ProductRepository;
import com.retailpos.domain.SalesOrder;
import com.retailpos.domain.SalesOrderRepository;
import com.retailpos.inventory.JuiceBatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/pos", "/api"})
@CrossOrigin(origins = "*")
public class POSController {

    private final POSService posService;
    private final ProductRepository productRepository;
    private final JuiceBatchService juiceBatchService;
    private final SalesOrderRepository salesOrderRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public POSController(POSService posService, ProductRepository productRepository, JuiceBatchService juiceBatchService, SalesOrderRepository salesOrderRepository, PriceHistoryRepository priceHistoryRepository, SimpMessagingTemplate messagingTemplate) {
        this.posService = posService;
        this.productRepository = productRepository;
        this.juiceBatchService = juiceBatchService;
        this.salesOrderRepository = salesOrderRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.messagingTemplate = messagingTemplate;
    }

    private void broadcastProductUpdate() {
        try {
            List<Product> allProducts = productRepository.findByIsActiveTrueOrderByIdAsc();
            messagingTemplate.convertAndSend("/topic/prices", allProducts);
            messagingTemplate.convertAndSend("/topic/products", allProducts);
        } catch (Exception e) {}
    }

    @GetMapping("/products")
    public ResponseEntity<List<Product>> getAvailableProducts() {
        return ResponseEntity.ok(productRepository.findByIsActiveTrueOrderByIdAsc());
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/products/{id}/price")
    public ResponseEntity<Map<String, Object>> getProductPrice(@PathVariable Long id) {
        return productRepository.findById(id).map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("productId", p.getId());
            map.put("productName", p.getName());
            map.put("flavour", p.getFlavour());
            map.put("currentCupPrice", p.getCurrentCupPrice());
            map.put("minCupPrice", p.getMinCupPrice());
            map.put("maxCupPrice", p.getMaxCupPrice());
            map.put("lastUpdated", p.getLastPriceChangeTimestamp());
            return ResponseEntity.ok(map);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/products/{id}/price-history")
    public ResponseEntity<List<PriceHistory>> getProductPriceHistory(@PathVariable Long id) {
        return ResponseEntity.ok(priceHistoryRepository.findByProductIdOrderByCreatedAtDesc(id));
    }

    public static class StockUpdateRequest {
        private Integer volumeMl;
        public Integer getVolumeMl() { return volumeMl; }
        public void setVolumeMl(Integer volumeMl) { this.volumeMl = volumeMl; }
    }

    @PutMapping("/products/{id}/stock")
    public ResponseEntity<Map<String, Object>> updateStock(@PathVariable Long id, @RequestBody StockUpdateRequest req) {
        if (!productRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        int volume = (req != null && req.getVolumeMl() != null) ? req.getVolumeMl() : 20000;
        juiceBatchService.registerNewBatch(id, volume);
        Map<String, Object> res = new HashMap<>();
        res.put("productId", id);
        res.put("addedVolumeMl", volume);
        res.put("message", "Stock refilled successfully");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/products")
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        if (product.getCurrentCupPrice() == null) {
            product.setCurrentCupPrice(product.getMinCupPrice() != null ? product.getMinCupPrice() : BigDecimal.valueOf(25));
        }
        if (product.getDefaultCupSizeMl() == null) {
            product.setDefaultCupSizeMl(250);
        }
        if (product.getMinCupPrice() == null) {
            product.setMinCupPrice(BigDecimal.valueOf(18));
        }
        if (product.getMaxCupPrice() == null) {
            product.setMaxCupPrice(BigDecimal.valueOf(35));
        }
        product.setLastPriceChangeTimestamp(LocalDateTime.now());
        Product saved = productRepository.save(product);

        // Auto-register initial 20L container batch for new product
        try {
            juiceBatchService.registerNewBatch(saved.getId(), 20000);
        } catch (Exception e) {}

        broadcastProductUpdate();
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id, @RequestBody Product details) {
        return productRepository.findById(id).map(existing -> {
            if (details.getName() != null) existing.setName(details.getName());
            if (details.getFlavour() != null) existing.setFlavour(details.getFlavour());
            if (details.getDescription() != null) existing.setDescription(details.getDescription());
            if (details.getCurrentCupPrice() != null) existing.setCurrentCupPrice(details.getCurrentCupPrice());
            if (details.getMinCupPrice() != null) existing.setMinCupPrice(details.getMinCupPrice());
            if (details.getMaxCupPrice() != null) existing.setMaxCupPrice(details.getMaxCupPrice());
            if (details.getDefaultCupPrice() != null) existing.setDefaultCupPrice(details.getDefaultCupPrice());
            if (details.getDefaultCupSizeMl() != null) existing.setDefaultCupSizeMl(details.getDefaultCupSizeMl());
            if (details.getTargetSalesPer1Minute() != null) {
                existing.setTargetSalesPer1Minute(details.getTargetSalesPer1Minute());
                existing.setTargetSalesPer2Minute(details.getTargetSalesPer1Minute() * 2.0);
            } else if (details.getTargetSalesPer2Minute() != null) {
                existing.setTargetSalesPer2Minute(details.getTargetSalesPer2Minute());
                existing.setTargetSalesPer1Minute(details.getTargetSalesPer2Minute() / 2.0);
            }
            if (details.getTargetOrders() != null) existing.setTargetOrders(details.getTargetOrders());
            if (details.getVolatility() != null) existing.setVolatility(details.getVolatility());
            existing.setLastPriceChangeTimestamp(LocalDateTime.now());
            existing.setPriceVersion(existing.getPriceVersion() != null ? existing.getPriceVersion() + 1 : 1);
            Product updated = productRepository.save(existing);
            broadcastProductUpdate();
            return ResponseEntity.ok(updated);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            broadcastProductUpdate();
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping({"/checkout", "/orders"})
    public ResponseEntity<?> checkout(
            @RequestBody POSService.CheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {
        if (request != null && request.getItems() != null) {
            for (POSService.CartItemRequest item : request.getItems()) {
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    Map<String, Object> errBody = new HashMap<>();
                    errBody.put("success", false);
                    errBody.put("status", 400);
                    errBody.put("error", "BAD_REQUEST");
                    errBody.put("message", "Invalid quantity: Item quantity must be greater than 0");
                    return ResponseEntity.badRequest().body(errBody);
                }
            }
        }

        if (request != null && (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank())) {
            if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
                request.setIdempotencyKey(idempotencyKeyHeader);
            }
        }
        try {
            POSService.CheckoutResponse response = posService.processCheckout(request);
            return ResponseEntity.ok(response);
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            if (request != null && request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
                for (int attempt = 0; attempt < 30; attempt++) {
                    java.util.Optional<SalesOrder> existingOrder = salesOrderRepository.findByIdempotencyKey(request.getIdempotencyKey());
                    if (existingOrder.isPresent()) {
                        SalesOrder existing = existingOrder.get();
                        List<POSService.OrderItemResponse> existingItems = existing.getItems().stream().map(i ->
                                POSService.OrderItemResponse.builder()
                                        .productName(i.getProductName())
                                        .quantity(i.getQuantity())
                                        .cupSizeMl(i.getCupSizeMl())
                                        .unitPrice(i.getUnitPrice())
                                        .totalPrice(i.getTotalPrice())
                                        .volumeDeductedMl(i.getVolumeDeductedMl())
                                        .build()
                        ).toList();

                        POSService.CheckoutResponse dupResp = POSService.CheckoutResponse.builder()
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
                        return ResponseEntity.ok(dupResp);
                    }
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                }
            }
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "POS checkout failed: " + dive.getMessage());
            return ResponseEntity.internalServerError().body(err);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "POS checkout failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<List<SalesOrder>> getAllOrders() {
        return ResponseEntity.ok(salesOrderRepository.findAll());
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<SalesOrder> getOrderById(@PathVariable Long id) {
        return salesOrderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
