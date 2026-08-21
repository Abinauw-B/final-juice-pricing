package com.retailpos.pricing;

import com.retailpos.domain.PriceHistory;
import com.retailpos.domain.PriceHistoryRepository;
import com.retailpos.domain.Product;
import com.retailpos.domain.ProductRepository;
import com.retailpos.domain.SystemConfig;
import com.retailpos.domain.SystemConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/pricing", "/api/admin/pricing"})
@CrossOrigin(origins = "*")
public class PricingController {

    private final PriceAdjustmentService priceAdjustmentService;
    private final PricingSimulationService pricingSimulationService;
    private final PriceHistoryRepository priceHistoryRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ProductRepository productRepository;
    private final MarketCrashService marketCrashService;

    private final SimpMessagingTemplate messagingTemplate;

    public PricingController(PriceAdjustmentService priceAdjustmentService, PricingSimulationService pricingSimulationService, PriceHistoryRepository priceHistoryRepository, SystemConfigRepository systemConfigRepository, ProductRepository productRepository, MarketCrashService marketCrashService, SimpMessagingTemplate messagingTemplate) {
        this.priceAdjustmentService = priceAdjustmentService;
        this.pricingSimulationService = pricingSimulationService;
        this.priceHistoryRepository = priceHistoryRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.productRepository = productRepository;
        this.marketCrashService = marketCrashService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping({"/live", "/products"})
    public ResponseEntity<List<Product>> getLivePrices() {
        return ResponseEntity.ok(productRepository.findAll());
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<Product> getProductPricing(@PathVariable Long productId) {
        return ResponseEntity.of(productRepository.findById(productId));
    }

    @GetMapping("/products/{productId}/metrics")
    public ResponseEntity<Map<String, Object>> getProductMetrics(@PathVariable Long productId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("productId", p.getId());
        metrics.put("flavour", p.getFlavour());
        metrics.put("basePrice", p.getDefaultCupPrice());
        metrics.put("currentPrice", p.getCurrentCupPrice());
        metrics.put("minPrice", p.getMinCupPrice());
        metrics.put("maxPrice", p.getMaxCupPrice());
        metrics.put("lastPriceUpdate", p.getLastPriceChangeTimestamp());

        return ResponseEntity.ok(metrics);
    }

    @GetMapping({"/history/{productId}", "/history"})
    public ResponseEntity<List<PriceHistory>> getPriceHistory(@PathVariable(required = false) String productId) {
        if (productId != null && !productId.isBlank()) {
            try {
                Long pid = Long.parseLong(productId);
                return ResponseEntity.ok(priceHistoryRepository.findByProductIdOrderByCreatedAtDesc(pid));
            } catch (NumberFormatException nfe) {
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }
        }
        return ResponseEntity.ok(priceHistoryRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/products/{productId}/breakdown")
    public ResponseEntity<Map<String, Object>> getProductBreakdown(@PathVariable Long productId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        BigDecimal base = p.getDefaultCupPrice() != null ? p.getDefaultCupPrice() : new BigDecimal("20.00");
        BigDecimal current = p.getCurrentCupPrice() != null ? p.getCurrentCupPrice() : base;
        BigDecimal diff = current.subtract(base);
        double pct = base.compareTo(BigDecimal.ZERO) > 0 ? diff.doubleValue() / base.doubleValue() * 100.0 : 0.0;

        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("productId", p.getId());
        breakdown.put("productName", p.getName());
        breakdown.put("flavour", p.getFlavour());
        breakdown.put("basePrice", base);
        breakdown.put("currentPrice", current);
        breakdown.put("minPrice", p.getMinCupPrice());
        breakdown.put("maxPrice", p.getMaxCupPrice());
        breakdown.put("priceChange", diff);
        breakdown.put("priceChangePercent", Math.round(pct * 100.0) / 100.0);
        breakdown.put("trend", current.compareTo(base) > 0 ? "UP" : (current.compareTo(base) < 0 ? "DOWN" : "STABLE"));
        breakdown.put("demandScore", 1.0);
        breakdown.put("inventoryRatio", 1.0);
        breakdown.put("demandPressure", 0.0);
        breakdown.put("inventoryPressure", 0.0);
        breakdown.put("trendPressure", 0.0);
        breakdown.put("timePressure", 0.0);
        breakdown.put("totalPressure", 0.0);
        breakdown.put("rawPrice", current);
        breakdown.put("smoothedPrice", current);
        breakdown.put("previousPrice", base);
        breakdown.put("finalPrice", current);

        return ResponseEntity.ok(breakdown);
    }

    @PostMapping({"/products/{productId}/price", "/admin/products/{productId}/price"})
    public ResponseEntity<PriceAdjustmentService.PriceEvaluationResult> updateManualPrice(
            @PathVariable Long productId,
            @RequestParam BigDecimal newPrice,
            @RequestParam(required = false, defaultValue = "MANUAL_ADMIN_CHANGE") String reason) {
        PriceAdjustmentService.PriceEvaluationResult res = priceAdjustmentService.updateManualPrice(productId, newPrice, reason);
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/prices", productRepository.findAll());
            }
        } catch (Exception e) {}
        return ResponseEntity.ok(res);
    }

    @PostMapping({"/deploy", "/admin/deploy"})
    public ResponseEntity<Map<String, Object>> deployAdminPricing(@RequestBody PriceAdjustmentService.AdminPricingDeployRequest request) {
        Product updated = priceAdjustmentService.deployAdminPricing(request);
        try {
            if (messagingTemplate != null) {
                Map<String, Object> wsPayload = new HashMap<>();
                wsPayload.put("productId", updated.getId());
                wsPayload.put("id", updated.getId());
                wsPayload.put("name", updated.getName());
                wsPayload.put("flavour", updated.getFlavour());
                wsPayload.put("currentCupPrice", updated.getCurrentCupPrice());
                wsPayload.put("currentPrice", updated.getCurrentCupPrice());
                wsPayload.put("defaultCupPrice", updated.getDefaultCupPrice());
                wsPayload.put("minCupPrice", updated.getMinCupPrice());
                wsPayload.put("maxCupPrice", updated.getMaxCupPrice());
                wsPayload.put("priceVersion", updated.getPriceVersion());
                wsPayload.put("timestamp", LocalDateTime.now().toString());

                messagingTemplate.convertAndSend("/topic/prices", wsPayload);
                messagingTemplate.convertAndSend("/topic/products", productRepository.findAll());
            }
        } catch (Exception e) {}

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("deployed", true);
        response.put("id", updated.getId());
        response.put("productId", updated.getId());
        response.put("name", updated.getName());
        response.put("productName", updated.getName());
        response.put("currentCupPrice", updated.getCurrentCupPrice());
        response.put("currentPrice", updated.getCurrentCupPrice());
        response.put("defaultCupPrice", updated.getDefaultCupPrice());
        response.put("minCupPrice", updated.getMinCupPrice());
        response.put("maxCupPrice", updated.getMaxCupPrice());
        response.put("priceVersion", updated.getPriceVersion());
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping({"/market-crash/status", "/crash/status"})
    public ResponseEntity<MarketCrashService.MarketCrashStatus> getMarketCrashStatus() {
        return ResponseEntity.ok(marketCrashService.getStatus());
    }

    @PostMapping({"/market-crash/trigger", "/market-crash", "/crash/trigger", "/crash"})
    public ResponseEntity<MarketCrashService.MarketCrashStatus> triggerMarketCrash(@RequestParam(required = false, defaultValue = "3") Integer durationMinutes) {
        int duration = (durationMinutes != null && durationMinutes > 0) ? durationMinutes : 3;
        return ResponseEntity.ok(marketCrashService.triggerMarketCrash(duration, "MANUAL_ADMIN"));
    }

    @PostMapping({"/market-crash/stop", "/crash/stop"})
    public ResponseEntity<MarketCrashService.MarketCrashStatus> stopMarketCrash() {
        return ResponseEntity.ok(marketCrashService.stopMarketCrash());
    }

    @PostMapping({"/reset-all", "/admin/reset-all"})
    public ResponseEntity<List<Product>> resetAllPrices() {
        return ResponseEntity.ok(priceAdjustmentService.resetAllProductsToDefault());
    }

    @RequestMapping(value = {"/evaluate", "/admin/evaluate"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<List<PriceAdjustmentService.PriceEvaluationResult>> evaluateAllPrices() {
        List<PriceAdjustmentService.PriceEvaluationResult> list = priceAdjustmentService.evaluateAllProducts();
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/prices", productRepository.findAll());
            }
        } catch (Exception e) {}
        return ResponseEntity.ok(list);
    }

    @PostMapping("/evaluate/{productId}")
    public ResponseEntity<PriceAdjustmentService.PriceEvaluationResult> evaluateProductPrice(@PathVariable Long productId) {
        PriceAdjustmentService.PriceEvaluationResult res = priceAdjustmentService.evaluateAndAdjustPrice(productId);
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/prices", productRepository.findAll());
            }
        } catch (Exception e) {}
        return ResponseEntity.ok(res);
    }

    @PostMapping("/simulate")
    public ResponseEntity<PricingSimulationService.SimulationResponse> simulatePricing(@RequestBody PricingSimulationService.SimulationRequest request) {
        return ResponseEntity.ok(pricingSimulationService.runSimulation(request));
    }

    @GetMapping({"/config", "/admin/config"})
    public ResponseEntity<List<SystemConfig>> getConfig() {
        return ResponseEntity.ok(systemConfigRepository.findAll());
    }

    public static class UpdateConfigItem {
        private String key;
        private String value;

        public UpdateConfigItem() {}
        public UpdateConfigItem(String key, String value) {
            this.key = key;
            this.value = value;
        }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    @PutMapping({"/config", "/admin/config"})
    public ResponseEntity<List<SystemConfig>> updateConfig(@RequestBody List<UpdateConfigItem> updates) {
        for (UpdateConfigItem item : updates) {
            systemConfigRepository.findById(item.getKey()).ifPresent(cfg -> {
                cfg.setConfigValue(item.getValue());
                systemConfigRepository.save(cfg);
            });
        }
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/prices", productRepository.findAll());
            }
        } catch (Exception e) {}
        return ResponseEntity.ok(systemConfigRepository.findAll());
    }
}

