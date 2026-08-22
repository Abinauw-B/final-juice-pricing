package com.retailpos.pricing;

import com.retailpos.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PriceAdjustmentService {

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final JuiceBatchRepository juiceBatchRepository;
    private final MarketCrashService marketCrashService;
    private final AuditLogRepository auditLogRepository;

    private static LocalDateTime marketStartTime = LocalDateTime.now();
    private static boolean marketPaused = false;

    public PriceAdjustmentService(ProductRepository productRepository, PriceHistoryRepository priceHistoryRepository, SystemConfigRepository systemConfigRepository, SalesOrderItemRepository salesOrderItemRepository, JuiceBatchRepository juiceBatchRepository, MarketCrashService marketCrashService, AuditLogRepository auditLogRepository) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.juiceBatchRepository = juiceBatchRepository;
        this.marketCrashService = marketCrashService;
        this.auditLogRepository = auditLogRepository;
    }

    public static boolean isMarketPaused() {
        return marketPaused;
    }

    public static void setMarketPaused(boolean paused) {
        marketPaused = paused;
    }

    public static void resetMarketStartTime() {
        marketStartTime = LocalDateTime.now();
    }

    public static class PriceEvaluationResult {
        private Long productId;
        private String flavour;
        private BigDecimal oldPrice;
        private BigDecimal newPrice;
        private BigDecimal priceChange;
        private boolean priceChanged;
        private double demandRatio;
        private double weightedSales;
        private double targetSales;
        private String demandLevelCategory; // VERY_LOW, LOW, NORMAL, HIGH, VERY_HIGH
        private String explanation;
        private String statusReason;

        public PriceEvaluationResult() {}

        public PriceEvaluationResult(Long productId, String flavour, BigDecimal oldPrice, BigDecimal newPrice, BigDecimal priceChange, boolean priceChanged, double demandRatio, double weightedSales, double targetSales, String demandLevelCategory, String explanation, String statusReason) {
            this.productId = productId;
            this.flavour = flavour;
            this.oldPrice = oldPrice;
            this.newPrice = newPrice;
            this.priceChange = priceChange;
            this.priceChanged = priceChanged;
            this.demandRatio = demandRatio;
            this.weightedSales = weightedSales;
            this.targetSales = targetSales;
            this.demandLevelCategory = demandLevelCategory;
            this.explanation = explanation;
            this.statusReason = statusReason;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getFlavour() { return flavour; }
        public void setFlavour(String flavour) { this.flavour = flavour; }
        public BigDecimal getOldPrice() { return oldPrice; }
        public void setOldPrice(BigDecimal oldPrice) { this.oldPrice = oldPrice; }
        public BigDecimal getNewPrice() { return newPrice; }
        public void setNewPrice(BigDecimal newPrice) { this.newPrice = newPrice; }
        public BigDecimal getPriceChange() { return priceChange; }
        public void setPriceChange(BigDecimal priceChange) { this.priceChange = priceChange; }
        public boolean isPriceChanged() { return priceChanged; }
        public void setPriceChanged(boolean priceChanged) { this.priceChanged = priceChanged; }
        public double getDemandRatio() { return demandRatio; }
        public void setDemandRatio(double demandRatio) { this.demandRatio = demandRatio; }
        public double getWeightedSales() { return weightedSales; }
        public void setWeightedSales(double weightedSales) { this.weightedSales = weightedSales; }
        public double getTargetSales() { return targetSales; }
        public void setTargetSales(double targetSales) { this.targetSales = targetSales; }
        public String getDemandLevelCategory() { return demandLevelCategory; }
        public void setDemandLevelCategory(String demandLevelCategory) { this.demandLevelCategory = demandLevelCategory; }
        public double getDemandScore() { return demandRatio * 50.0; } // Backwards compatibility for existing UI
        public double getStockPressurePct() { return 0.0; }
        public double getTimeFactorMultiplier() { return 1.0; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        public String getStatusReason() { return statusReason; }
        public void setStatusReason(String statusReason) { this.statusReason = statusReason; }

        public static PriceEvaluationResultBuilder builder() { return new PriceEvaluationResultBuilder(); }

        public static class PriceEvaluationResultBuilder {
            private Long productId;
            private String flavour;
            private BigDecimal oldPrice;
            private BigDecimal newPrice;
            private BigDecimal priceChange;
            private boolean priceChanged;
            private double demandRatio;
            private double weightedSales;
            private double targetSales;
            private String demandLevelCategory;
            private String explanation;
            private String statusReason;

            public PriceEvaluationResultBuilder productId(Long productId) { this.productId = productId; return this; }
            public PriceEvaluationResultBuilder flavour(String flavour) { this.flavour = flavour; return this; }
            public PriceEvaluationResultBuilder oldPrice(BigDecimal oldPrice) { this.oldPrice = oldPrice; return this; }
            public PriceEvaluationResultBuilder newPrice(BigDecimal newPrice) { this.newPrice = newPrice; return this; }
            public PriceEvaluationResultBuilder priceChange(BigDecimal priceChange) { this.priceChange = priceChange; return this; }
            public PriceEvaluationResultBuilder priceChanged(boolean priceChanged) { this.priceChanged = priceChanged; return this; }
            public PriceEvaluationResultBuilder demandRatio(double demandRatio) { this.demandRatio = demandRatio; return this; }
            public PriceEvaluationResultBuilder weightedSales(double weightedSales) { this.weightedSales = weightedSales; return this; }
            public PriceEvaluationResultBuilder targetSales(double targetSales) { this.targetSales = targetSales; return this; }
            public PriceEvaluationResultBuilder demandLevelCategory(String demandLevelCategory) { this.demandLevelCategory = demandLevelCategory; return this; }
            public PriceEvaluationResultBuilder explanation(String explanation) { this.explanation = explanation; return this; }
            public PriceEvaluationResultBuilder statusReason(String statusReason) { this.statusReason = statusReason; return this; }

            public PriceEvaluationResult build() {
                return new PriceEvaluationResult(productId, flavour, oldPrice, newPrice, priceChange, priceChanged, demandRatio, weightedSales, targetSales, demandLevelCategory, explanation, statusReason);
            }
        }
    }

    @Transactional
    public PriceEvaluationResult evaluateAndAdjustPrice(Long productId) {
        return evaluateAndAdjustPrice(productId, LocalDateTime.now());
    }

    @Transactional
    public PriceEvaluationResult evaluateAndAdjustPrice(Long productId, LocalDateTime evaluationTime) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        LocalDateTime now = evaluationTime != null ? evaluationTime : LocalDateTime.now();

        BigDecimal oldPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : (product.getDefaultCupPrice() != null ? product.getDefaultCupPrice() : new BigDecimal("25.00"));
        BigDecimal floor = product.getMinCupPrice() != null ? product.getMinCupPrice() : new BigDecimal("18.00");
        BigDecimal ceiling = product.getMaxCupPrice() != null ? product.getMaxCupPrice() : new BigDecimal("35.00");
        double targetSales = product.getTargetSalesPer2Minute() != null ? product.getTargetSalesPer2Minute() : 1.0;

        // Check Market Paused
        if (marketPaused) {
            return PriceEvaluationResult.builder()
                    .productId(productId)
                    .flavour(product.getFlavour())
                    .oldPrice(oldPrice)
                    .newPrice(oldPrice)
                    .priceChange(BigDecimal.ZERO)
                    .priceChanged(false)
                    .demandRatio(1.0)
                    .weightedSales(targetSales)
                    .targetSales(targetSales)
                    .demandLevelCategory("NORMAL")
                    .explanation("Exchange is currently PAUSED by Admin. Prices held stable.")
                    .statusReason("MARKET_PAUSED")
                    .build();
        }

        // 1. Calculate sales across three 2-minute windows
        // W0 = current 2-min window (now-2m to now)
        // W1 = previous 2-min window (now-4m to now-2m)
        // W2 = previous previous 2-min window (now-6m to now-4m)
        int w0 = salesOrderItemRepository.countQuantitySoldForProductBetween(productId, now.minusMinutes(2), now);
        int w1 = salesOrderItemRepository.countQuantitySoldForProductBetween(productId, now.minusMinutes(4), now.minusMinutes(2));
        int w2 = salesOrderItemRepository.countQuantitySoldForProductBetween(productId, now.minusMinutes(6), now.minusMinutes(4));

        // 2. Weighted Sales Calculation: 0.50*W0 + 0.30*W1 + 0.20*W2
        double weightedSales = 0.50 * w0 + 0.30 * w1 + 0.20 * w2;

        // 3. Demand Ratio Calculation: weighted_sales / target_sales
        double demandRatio = (targetSales > 0) ? (weightedSales / targetSales) : 0.0;

        log.info("[PRICING WINDOW] productId={} W0={} W1={} W2={}", productId, w0, w1, w2);
        log.info("[DEMAND] productId={} weightedSales={} targetSales={} demandRatio={}", productId, weightedSales, targetSales, demandRatio);

        // 4. Exact Symmetric Price Movement Rules
        int priceMovement = 0;
        String demandCategory = "NORMAL";

        if (demandRatio < 0.20) {
            priceMovement = -2;
            demandCategory = "EXTREMELY_LOW";
        } else if (demandRatio < 0.50) {
            priceMovement = -2;
            demandCategory = "VERY_LOW";
        } else if (demandRatio < 0.70) {
            priceMovement = -1;
            demandCategory = "LOW";
        } else if (demandRatio < 0.90) {
            priceMovement = -1;
            demandCategory = "BELOW_NORMAL";
        } else if (demandRatio < 1.10) {
            priceMovement = 0;
            demandCategory = "NORMAL";
        } else if (demandRatio < 1.30) {
            priceMovement = 1;
            demandCategory = "ABOVE_NORMAL";
        } else if (demandRatio < 1.70) {
            priceMovement = 1;
            demandCategory = "HIGH";
        } else if (demandRatio < 2.00) {
            priceMovement = 2;
            demandCategory = "VERY_HIGH";
        } else {
            priceMovement = 2;
            demandCategory = "EXTREMELY_HIGH";
        }

        // 5. Constrain Price between Floor (₹18) and Ceiling (₹35)
        BigDecimal rawNewPrice = oldPrice.add(BigDecimal.valueOf(priceMovement));
        BigDecimal newPrice = rawNewPrice.max(floor).min(ceiling);
        BigDecimal priceChange = newPrice.subtract(oldPrice);
        boolean changed = priceChange.compareTo(BigDecimal.ZERO) != 0;

        log.info("[PRICE MOVEMENT] productId={} oldPrice={} movement={} newPrice={}", productId, oldPrice, priceMovement, newPrice);

        String explanation = String.format(
                "Demand %s (Ratio: %.2f, Weighted Sales: %.2f [W0=%d, W1=%d, W2=%d], Target: %.2f). Price movement %+d => ₹%s -> ₹%s.",
                demandCategory, demandRatio, weightedSales, w0, w1, w2, targetSales, priceMovement, oldPrice, newPrice
        );

        if (changed) {
            product.setCurrentCupPrice(newPrice);
            product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
            product.setLastPriceChangeTimestamp(now);
            productRepository.save(product);
            log.info("[DB PRICE SAVED] productId={} price={} priceVersion={}", productId, newPrice, product.getPriceVersion());
        }

        // Always log Price History record for each settlement execution audit
        PriceHistory history = PriceHistory.builder()
                .productId(productId)
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .priceChange(priceChange)
                .demandRatio(demandRatio)
                .weightedSales(weightedSales)
                .targetSales(targetSales)
                .calculationWindowStart(now.minusMinutes(6))
                .calculationWindowEnd(now)
                .reason(demandCategory)
                .explanation(explanation)
                .createdAt(now)
                .build();
        priceHistoryRepository.save(history);

        return PriceEvaluationResult.builder()
                .productId(productId)
                .flavour(product.getFlavour())
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .priceChange(priceChange)
                .priceChanged(changed)
                .demandRatio(demandRatio)
                .weightedSales(weightedSales)
                .targetSales(targetSales)
                .demandLevelCategory(demandCategory)
                .explanation(explanation)
                .statusReason(changed ? "PRICE_SETTLED" : "PRICE_STABLE")
                .build();
    }

    @Transactional
    public List<PriceEvaluationResult> evaluateAllProducts() {
        List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
        return products.stream()
                .map(p -> evaluateAndAdjustPrice(p.getId()))
                .toList();
    }

    @Transactional
    public PriceEvaluationResult updateManualPrice(Long productId, BigDecimal newPrice, String reason) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        BigDecimal floor = product.getMinCupPrice() != null ? product.getMinCupPrice() : new BigDecimal("18.00");
        BigDecimal ceiling = product.getMaxCupPrice() != null ? product.getMaxCupPrice() : new BigDecimal("35.00");

        if (newPrice.compareTo(floor) < 0) {
            throw new IllegalArgumentException("Price cannot be less than minimum price of ₹" + floor);
        }
        if (newPrice.compareTo(ceiling) > 0) {
            throw new IllegalArgumentException("Price cannot exceed maximum price of ₹" + ceiling);
        }

        BigDecimal oldPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : product.getDefaultCupPrice();
        LocalDateTime now = LocalDateTime.now();
        BigDecimal priceChange = newPrice.subtract(oldPrice);
        boolean changed = oldPrice.compareTo(newPrice) != 0;

        product.setCurrentCupPrice(newPrice);
        if (changed) {
            product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
        }
        product.setLastPriceChangeTimestamp(now);
        productRepository.save(product);

        if (changed) {
            PriceHistory history = PriceHistory.builder()
                    .productId(productId)
                    .oldPrice(oldPrice)
                    .newPrice(newPrice)
                    .priceChange(priceChange)
                    .demandRatio(1.0)
                    .weightedSales(1.0)
                    .targetSales(1.0)
                    .calculationWindowStart(now)
                    .calculationWindowEnd(now)
                    .reason("MANUAL_ADMIN_CHANGE")
                    .explanation(reason != null && !reason.isBlank() ? reason : "MANUAL_ADMIN_CHANGE")
                    .createdAt(now)
                    .build();
            priceHistoryRepository.save(history);
        }

        return PriceEvaluationResult.builder()
                .productId(productId)
                .flavour(product.getFlavour())
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .priceChange(priceChange)
                .priceChanged(changed)
                .demandRatio(1.0)
                .weightedSales(1.0)
                .targetSales(1.0)
                .demandLevelCategory("NORMAL")
                .explanation("Price manually set to ₹" + newPrice + " (" + reason + ")")
                .statusReason("MANUAL_ADMIN_CHANGE")
                .build();
    }

    public static class ResetAllResponse {
        private boolean success;
        private String message;
        private int productsReset;
        private String requestId;
        private String timestamp;
        private List<Product> prices;

        public ResetAllResponse() {}
        public ResetAllResponse(boolean success, String message, int productsReset, String requestId, String timestamp, List<Product> prices) {
            this.success = success;
            this.message = message;
            this.productsReset = productsReset;
            this.requestId = requestId;
            this.timestamp = timestamp;
            this.prices = prices;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public int getProductsReset() { return productsReset; }
        public void setProductsReset(int productsReset) { this.productsReset = productsReset; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public List<Product> getPrices() { return prices; }
        public void setPrices(List<Product> prices) { this.prices = prices; }
    }

    @Transactional
    public ResetAllResponse resetAllProductsToDefault(String reqId, String actor) {
        String requestId = (reqId != null && !reqId.isBlank()) ? reqId : "REQ-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String userActor = (actor != null && !actor.isBlank()) ? actor : "ADMIN";

        if (marketCrashService != null && marketCrashService.isCrashActive()) {
            marketCrashService.stopMarketCrash();
        }

        List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
        LocalDateTime now = LocalDateTime.now();
        resetMarketStartTime();

        int resetCount = 0;
        for (Product p : products) {
            BigDecimal oldPrice = p.getCurrentCupPrice();
            BigDecimal basePrice = new BigDecimal("25.00");

            p.setCurrentCupPrice(basePrice);
            p.setDefaultCupPrice(basePrice);
            p.setMinCupPrice(new BigDecimal("18.00"));
            p.setMaxCupPrice(new BigDecimal("35.00"));
            p.setPriceVersion((p.getPriceVersion() != null ? p.getPriceVersion() : 1) + 1);
            p.setLastPriceChangeTimestamp(now);
            productRepository.save(p);
            resetCount++;

            PriceHistory history = PriceHistory.builder()
                    .productId(p.getId())
                    .oldPrice(oldPrice != null ? oldPrice : basePrice)
                    .newPrice(basePrice)
                    .priceChange(basePrice.subtract(oldPrice != null ? oldPrice : basePrice))
                    .demandRatio(1.0)
                    .weightedSales(1.0)
                    .targetSales(1.0)
                    .calculationWindowStart(now)
                    .calculationWindowEnd(now)
                    .reason("DAILY_MARKET_RESET")
                    .explanation(String.format("DAILY_MARKET_RESET: Reset from ₹%s to base ₹25.00. Actor: %s", oldPrice, userActor))
                    .createdAt(now)
                    .build();
            priceHistoryRepository.save(history);
        }

        List<Product> updatedList = productRepository.findByIsActiveTrueOrderByIdAsc();

        return new ResetAllResponse(
                true,
                "All market prices reset to base ₹25.00 successfully",
                resetCount,
                requestId,
                now.toString(),
                updatedList
        );
    }

    public static class AdminPricingDeployRequest {
        private Long productId;
        private String flavour;
        private BigDecimal currentCupPrice;
        private BigDecimal minCupPrice;
        private BigDecimal maxCupPrice;
        private Double targetSalesPer2Minute;

        public AdminPricingDeployRequest() {}
        public AdminPricingDeployRequest(Long productId, String flavour, BigDecimal currentCupPrice, BigDecimal minCupPrice, BigDecimal maxCupPrice, Double targetSalesPer2Minute) {
            this.productId = productId;
            this.flavour = flavour;
            this.currentCupPrice = currentCupPrice;
            this.minCupPrice = minCupPrice;
            this.maxCupPrice = maxCupPrice;
            this.targetSalesPer2Minute = targetSalesPer2Minute;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getFlavour() { return flavour; }
        public void setFlavour(String flavour) { this.flavour = flavour; }
        public BigDecimal getCurrentCupPrice() { return currentCupPrice; }
        public void setCurrentCupPrice(BigDecimal currentCupPrice) { this.currentCupPrice = currentCupPrice; }
        public BigDecimal getCurrentPrice() { return currentCupPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentCupPrice = currentPrice; }
        public BigDecimal getMinCupPrice() { return minCupPrice; }
        public void setMinCupPrice(BigDecimal minCupPrice) { this.minCupPrice = minCupPrice; }
        public BigDecimal getMaxCupPrice() { return maxCupPrice; }
        public void setMaxCupPrice(BigDecimal maxCupPrice) { this.maxCupPrice = maxCupPrice; }
        public Double getTargetSalesPer2Minute() { return targetSalesPer2Minute; }
        public void setTargetSalesPer2Minute(Double targetSalesPer2Minute) { this.targetSalesPer2Minute = targetSalesPer2Minute; }
    }

    @Transactional
    public Product deployAdminPricing(AdminPricingDeployRequest request) {
        if (request == null || request.getProductId() == null) {
            throw new IllegalArgumentException("Product ID is required for deploy");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + request.getProductId()));

        BigDecimal oldPrice = product.getCurrentCupPrice();
        BigDecimal newPrice = request.getCurrentCupPrice() != null ? request.getCurrentCupPrice() : product.getCurrentCupPrice();

        if (request.getMinCupPrice() != null) product.setMinCupPrice(request.getMinCupPrice());
        if (request.getMaxCupPrice() != null) product.setMaxCupPrice(request.getMaxCupPrice());
        if (request.getTargetSalesPer2Minute() != null) product.setTargetSalesPer2Minute(request.getTargetSalesPer2Minute());
        if (newPrice != null) product.setCurrentCupPrice(newPrice);

        product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
        product.setLastPriceChangeTimestamp(LocalDateTime.now());

        Product saved = productRepository.save(product);

        PriceHistory history = PriceHistory.builder()
                .productId(saved.getId())
                .oldPrice(oldPrice != null ? oldPrice : newPrice)
                .newPrice(newPrice)
                .priceChange(newPrice.subtract(oldPrice != null ? oldPrice : newPrice))
                .reason("ADMIN_DEPLOY")
                .explanation("Admin deployed product config updates.")
                .createdAt(LocalDateTime.now())
                .build();
        priceHistoryRepository.save(history);

        return saved;
    }

    public static class PriceDebugDTO {
        private Long productId;
        private String productName;
        private String flavour;
        private BigDecimal currentPrice;
        private double targetSales;
        private int windowW0;
        private int windowW1;
        private int windowW2;
        private double weightedSales;
        private double demandRatio;
        private String demandLevelCategory;
        private int priceMovement;
        private BigDecimal projectedNewPrice;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private String calculationBreakdown;

        public PriceDebugDTO() {}

        public PriceDebugDTO(Long productId, String productName, String flavour, BigDecimal currentPrice, double targetSales, int windowW0, int windowW1, int windowW2, double weightedSales, double demandRatio, String demandLevelCategory, int priceMovement, BigDecimal projectedNewPrice, BigDecimal minPrice, BigDecimal maxPrice, String calculationBreakdown) {
            this.productId = productId;
            this.productName = productName;
            this.flavour = flavour;
            this.currentPrice = currentPrice;
            this.targetSales = targetSales;
            this.windowW0 = windowW0;
            this.windowW1 = windowW1;
            this.windowW2 = windowW2;
            this.weightedSales = weightedSales;
            this.demandRatio = demandRatio;
            this.demandLevelCategory = demandLevelCategory;
            this.priceMovement = priceMovement;
            this.projectedNewPrice = projectedNewPrice;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.calculationBreakdown = calculationBreakdown;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getFlavour() { return flavour; }
        public void setFlavour(String flavour) { this.flavour = flavour; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        public double getTargetSales() { return targetSales; }
        public void setTargetSales(double targetSales) { this.targetSales = targetSales; }
        public int getWindowW0() { return windowW0; }
        public void setWindowW0(int windowW0) { this.windowW0 = windowW0; }
        public int getWindowW1() { return windowW1; }
        public void setWindowW1(int windowW1) { this.windowW1 = windowW1; }
        public int getWindowW2() { return windowW2; }
        public void setWindowW2(int windowW2) { this.windowW2 = windowW2; }
        public double getWeightedSales() { return weightedSales; }
        public void setWeightedSales(double weightedSales) { this.weightedSales = weightedSales; }
        public double getDemandRatio() { return demandRatio; }
        public void setDemandRatio(double demandRatio) { this.demandRatio = demandRatio; }
        public String getDemandLevelCategory() { return demandLevelCategory; }
        public void setDemandLevelCategory(String demandLevelCategory) { this.demandLevelCategory = demandLevelCategory; }
        public int getPriceMovement() { return priceMovement; }
        public void setPriceMovement(int priceMovement) { this.priceMovement = priceMovement; }
        public BigDecimal getProjectedNewPrice() { return projectedNewPrice; }
        public void setProjectedNewPrice(BigDecimal projectedNewPrice) { this.projectedNewPrice = projectedNewPrice; }
        public BigDecimal getMinPrice() { return minPrice; }
        public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
        public BigDecimal getMaxPrice() { return maxPrice; }
        public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
        public String getCalculationBreakdown() { return calculationBreakdown; }
        public void setCalculationBreakdown(String calculationBreakdown) { this.calculationBreakdown = calculationBreakdown; }
    }

    public PriceDebugDTO getDebugEvaluation(Long productId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        LocalDateTime now = LocalDateTime.now();

        BigDecimal currentPrice = p.getCurrentCupPrice() != null ? p.getCurrentCupPrice() : p.getDefaultCupPrice();
        BigDecimal floor = p.getMinCupPrice() != null ? p.getMinCupPrice() : new BigDecimal("18.00");
        BigDecimal ceiling = p.getMaxCupPrice() != null ? p.getMaxCupPrice() : new BigDecimal("35.00");
        double targetSales = p.getTargetSalesPer2Minute() != null ? p.getTargetSalesPer2Minute() : 1.0;

        int w0 = salesOrderItemRepository.countQuantitySoldForProductBetween(productId, now.minusMinutes(2), now);
        int w1 = salesOrderItemRepository.countQuantitySoldForProductBetween(productId, now.minusMinutes(4), now.minusMinutes(2));
        int w2 = salesOrderItemRepository.countQuantitySoldForProductBetween(productId, now.minusMinutes(6), now.minusMinutes(4));

        double weightedSales = 0.50 * w0 + 0.30 * w1 + 0.20 * w2;
        double demandRatio = (targetSales > 0) ? (weightedSales / targetSales) : 0.0;

        int movement = 0;
        String category = "NORMAL";
        if (demandRatio < 0.20) { movement = -2; category = "EXTREMELY_LOW"; }
        else if (demandRatio < 0.50) { movement = -2; category = "VERY_LOW"; }
        else if (demandRatio < 0.70) { movement = -1; category = "LOW"; }
        else if (demandRatio < 0.90) { movement = -1; category = "BELOW_NORMAL"; }
        else if (demandRatio < 1.10) { movement = 0; category = "NORMAL"; }
        else if (demandRatio < 1.30) { movement = 1; category = "ABOVE_NORMAL"; }
        else if (demandRatio < 1.70) { movement = 1; category = "HIGH"; }
        else if (demandRatio < 2.00) { movement = 2; category = "VERY_HIGH"; }
        else { movement = 2; category = "EXTREMELY_HIGH"; }

        BigDecimal projectedPrice = currentPrice.add(BigDecimal.valueOf(movement)).max(floor).min(ceiling);

        String breakdown = String.format(
                "Current Window W0: %d, Window W1: %d, Window W2: %d | Weighted Sales: 0.50*%d + 0.30*%d + 0.20*%d = %.2f | Target: %.2f | Demand Ratio: %.2f / %.2f = %.2f (%s) | Movement: %+d => Projected: ₹%s",
                w0, w1, w2, w0, w1, w2, weightedSales, targetSales, weightedSales, targetSales, demandRatio, category, movement, projectedPrice
        );

        return new PriceDebugDTO(
                p.getId(), p.getName(), p.getFlavour(), currentPrice, targetSales,
                w0, w1, w2, weightedSales, demandRatio, category, movement, projectedPrice, floor, ceiling, breakdown
        );
    }

    public List<PriceDebugDTO> getDebugEvaluationAll() {
        List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
        return products.stream().map(p -> getDebugEvaluation(p.getId())).toList();
    }
}
