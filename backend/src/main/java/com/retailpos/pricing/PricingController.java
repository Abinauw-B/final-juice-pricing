package com.retailpos.pricing;

import com.retailpos.domain.*;
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

    private final PricingEngineService pricingEngineService;
    private final PriceLockService priceLockService;
    private final com.retailpos.pricing.service.MarketCorrelationService marketCorrelationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PricingConfigurationService pricingConfigurationService;
    private final PricingConfigAuditLogRepository auditLogRepository;

    public PricingController(PriceAdjustmentService priceAdjustmentService,
                             PricingSimulationService pricingSimulationService,
                             PriceHistoryRepository priceHistoryRepository,
                             SystemConfigRepository systemConfigRepository,
                             ProductRepository productRepository,
                             MarketCrashService marketCrashService,
                             PricingEngineService pricingEngineService,
                             PriceLockService priceLockService,
                             com.retailpos.pricing.service.MarketCorrelationService marketCorrelationService,
                             SimpMessagingTemplate messagingTemplate,
                             PricingConfigurationService pricingConfigurationService,
                             PricingConfigAuditLogRepository auditLogRepository) {
        this.priceAdjustmentService = priceAdjustmentService;
        this.pricingSimulationService = pricingSimulationService;
        this.priceHistoryRepository = priceHistoryRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.productRepository = productRepository;
        this.marketCrashService = marketCrashService;
        this.pricingEngineService = pricingEngineService;
        this.priceLockService = priceLockService;
        this.marketCorrelationService = marketCorrelationService;
        this.messagingTemplate = messagingTemplate;
        this.pricingConfigurationService = pricingConfigurationService;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping({"/market", "/status", "/admin/status"})
    public ResponseEntity<PricingEngineService.PriceEvaluationCycleResult> getMarketState() {
        return ResponseEntity.ok(pricingEngineService.getCurrentMarketState());
    }

    @PostMapping({"/admin/pause", "/pause"})
    public ResponseEntity<Map<String, Object>> pauseExchange() {
        PriceAdjustmentService.setMarketPaused(true);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("status", "PAUSED");
        res.put("message", "Juice Exchange algorithm paused.");
        return ResponseEntity.ok(res);
    }

    @PostMapping({"/admin/resume", "/resume"})
    public ResponseEntity<Map<String, Object>> resumeExchange() {
        PriceAdjustmentService.setMarketPaused(false);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("status", "OPEN");
        res.put("message", "Juice Exchange algorithm resumed.");
        return ResponseEntity.ok(res);
    }

    @GetMapping({"/live", "/products"})
    public ResponseEntity<List<Product>> getLivePrices() {
        return ResponseEntity.ok(productRepository.findByIsActiveTrueOrderByIdAsc());
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
                List<Product> allProducts = productRepository.findByIsActiveTrueOrderByIdAsc();
                messagingTemplate.convertAndSend("/topic/prices", allProducts);
                messagingTemplate.convertAndSend("/topic/products", allProducts);
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
    public ResponseEntity<MarketCrashService.MarketCrashStatus> triggerMarketCrash(@RequestParam(required = false) Integer durationMinutes) {
        int duration = (durationMinutes != null && durationMinutes > 0) ? durationMinutes : 0;
        return ResponseEntity.ok(marketCrashService.triggerMarketCrash(duration, "MANUAL_ADMIN"));
    }

    @PostMapping({"/market-crash/stop", "/crash/stop"})
    public ResponseEntity<MarketCrashService.MarketCrashStatus> stopMarketCrash() {
        return ResponseEntity.ok(marketCrashService.stopMarketCrash());
    }

    @PostMapping({"/reset-all", "/admin/reset-all", "/reset", "/admin/reset"})
    public ResponseEntity<PriceAdjustmentService.ResetAllResponse> resetAllPrices(
            @RequestHeader(value = "X-Request-ID", required = false) String headerReqId,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        String reqId = (headerReqId != null && !headerReqId.isBlank()) ? headerReqId : "REQ-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String actor = (roleHeader != null && !roleHeader.isBlank()) ? roleHeader : "ADMIN";

        // 1. Transactional Database Reset Execution (Commit occurs before method returns)
        PriceAdjustmentService.ResetAllResponse response = priceAdjustmentService.resetAllProductsToDefault(reqId, actor);

        // 2. Broadcast STOMP ONLY AFTER TRANSACTION COMMIT SUCCESS
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/prices", response.getPrices());
                messagingTemplate.convertAndSend("/topic/products", response.getPrices());
            }
        } catch (Exception e) {}

        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = {"/evaluate", "/admin/evaluate"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<PricingEngineService.PriceEvaluationCycleResult> evaluateAllPrices() {
        PricingEngineService.PriceEvaluationCycleResult cycleResult = pricingEngineService.executeSettlementCycle(true);
        return ResponseEntity.ok(cycleResult);
    }

    @PostMapping("/evaluate/{productId}")
    public ResponseEntity<PriceAdjustmentService.PriceEvaluationResult> evaluateProductPrice(@PathVariable Long productId) {
        PriceAdjustmentService.PriceEvaluationResult res = priceAdjustmentService.evaluateAndAdjustPrice(productId);
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/prices", productRepository.findByIsActiveTrueOrderByIdAsc());
            }
        } catch (Exception e) {}
        return ResponseEntity.ok(res);
    }

    @GetMapping({"/debug", "/admin/debug"})
    public ResponseEntity<List<PriceAdjustmentService.PriceDebugDTO>> getDebugEvaluationAll() {
        return ResponseEntity.ok(priceAdjustmentService.getDebugEvaluationAll());
    }

    @GetMapping({"/debug/{productId}", "/admin/debug/{productId}"})
    public ResponseEntity<PriceAdjustmentService.PriceDebugDTO> getDebugEvaluation(@PathVariable Long productId) {
        return ResponseEntity.ok(priceAdjustmentService.getDebugEvaluation(productId));
    }

    @PostMapping("/simulate")
    public ResponseEntity<PricingSimulationService.SimulationResponse> simulatePricing(@RequestBody PricingSimulationService.SimulationRequest request) {
        return ResponseEntity.ok(pricingSimulationService.runSimulation(request));
    }

    @GetMapping({"/config", "/admin/config"})
    public ResponseEntity<com.retailpos.pricing.model.PricingConfigDTO> getConfig() {
        return ResponseEntity.ok(pricingConfigurationService.getFullConfiguration());
    }

    @PutMapping({"/config", "/admin/config"})
    public ResponseEntity<com.retailpos.pricing.model.PricingConfigDTO> updateConfig(
            @RequestBody com.retailpos.pricing.model.PricingConfigDTO.GlobalConfig globalConfig,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        String actor = (roleHeader != null && !roleHeader.isBlank()) ? roleHeader : "ADMIN";
        com.retailpos.pricing.model.PricingConfigDTO updated = pricingConfigurationService.updateGlobalConfiguration(globalConfig, actor, "ADMIN_UI_UPDATE");
        return ResponseEntity.ok(updated);
    }

    @GetMapping({"/products/{productId}/config", "/admin/products/{productId}/config"})
    public ResponseEntity<com.retailpos.pricing.model.PricingConfigDTO.ProductConfig> getProductConfig(@PathVariable Long productId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        return ResponseEntity.ok(new com.retailpos.pricing.model.PricingConfigDTO.ProductConfig(
                p.getId(), p.getName(), p.getFlavour(),
                p.getTargetSalesPer2Minute() != null ? p.getTargetSalesPer2Minute() : 1.0,
                p.getDefaultCupPrice(), p.getCurrentCupPrice(), p.getMinCupPrice(), p.getMaxCupPrice()
        ));
    }

    @PutMapping({"/products/{productId}/config", "/admin/products/{productId}/config"})
    public ResponseEntity<com.retailpos.pricing.model.PricingConfigDTO.ProductConfig> updateProductConfig(
            @PathVariable Long productId,
            @RequestBody com.retailpos.pricing.model.PricingConfigDTO.ProductConfig productConfig,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        String actor = (roleHeader != null && !roleHeader.isBlank()) ? roleHeader : "ADMIN";
        com.retailpos.pricing.model.PricingConfigDTO.ProductConfig updated = pricingConfigurationService.updateProductConfiguration(productId, productConfig, actor, "ADMIN_PRODUCT_CONFIG_UPDATE");
        return ResponseEntity.ok(updated);
    }

    @GetMapping({"/config/audit", "/admin/config/audit"})
    public ResponseEntity<List<com.retailpos.domain.PricingConfigAuditLog>> getPricingAuditLogs() {
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping({"/quote", "/lock"})
    public ResponseEntity<com.retailpos.pricing.model.PriceQuote> requestPriceQuote(
            @RequestParam Long productId,
            @RequestParam(required = false, defaultValue = "1") Integer quantity) {
        int qty = (quantity != null && quantity > 0) ? quantity : 1;
        return ResponseEntity.ok(priceLockService.createQuote(productId, qty));
    }

    @GetMapping({"/correlations", "/admin/correlations"})
    public ResponseEntity<List<com.retailpos.domain.ProductCorrelation>> getCorrelations() {
        return ResponseEntity.ok(marketCorrelationService.getAllCorrelations());
    }

    public static class UpdateCorrelationRequest {
        private Long sourceProductId;
        private Long targetProductId;
        private BigDecimal coefficient;
        private Boolean enabled;

        public UpdateCorrelationRequest() {}
        public Long getSourceProductId() { return sourceProductId; }
        public void setSourceProductId(Long sourceProductId) { this.sourceProductId = sourceProductId; }
        public Long getTargetProductId() { return targetProductId; }
        public void setTargetProductId(Long targetProductId) { this.targetProductId = targetProductId; }
        public BigDecimal getCoefficient() { return coefficient; }
        public void setCoefficient(BigDecimal coefficient) { this.coefficient = coefficient; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    @PutMapping({"/correlations", "/admin/correlations"})
    public ResponseEntity<com.retailpos.domain.ProductCorrelation> updateCorrelation(@RequestBody UpdateCorrelationRequest req) {
        return ResponseEntity.ok(marketCorrelationService.updateCorrelation(req.getSourceProductId(), req.getTargetProductId(), req.getCoefficient(), req.getEnabled()));
    }
}

