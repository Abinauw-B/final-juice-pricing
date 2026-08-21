package com.retailpos.pricing;

import com.retailpos.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class PriceAdjustmentService {

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final JuiceBatchRepository juiceBatchRepository;
    private final DemandCalculationService demandCalculationService;
    private final StockPressureService stockPressureService;
    private final TimeFactorService timeFactorService;
    private final MarketCrashService marketCrashService;
    private final AuditLogRepository auditLogRepository;

    public PriceAdjustmentService(ProductRepository productRepository, PriceHistoryRepository priceHistoryRepository, SystemConfigRepository systemConfigRepository, SalesOrderItemRepository salesOrderItemRepository, JuiceBatchRepository juiceBatchRepository, DemandCalculationService demandCalculationService, StockPressureService stockPressureService, TimeFactorService timeFactorService, MarketCrashService marketCrashService, AuditLogRepository auditLogRepository) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.juiceBatchRepository = juiceBatchRepository;
        this.demandCalculationService = demandCalculationService;
        this.stockPressureService = stockPressureService;
        this.timeFactorService = timeFactorService;
        this.marketCrashService = marketCrashService;
        this.auditLogRepository = auditLogRepository;
    }

    public static class PriceEvaluationResult {
        private Long productId;
        private String flavour;
        private BigDecimal oldPrice;
        private BigDecimal newPrice;
        private boolean priceChanged;
        private double demandScore;
        private double stockPressurePct;
        private double timeFactorMultiplier;
        private String explanation;
        private String statusReason;

        public PriceEvaluationResult() {}
        public PriceEvaluationResult(Long productId, String flavour, BigDecimal oldPrice, BigDecimal newPrice, boolean priceChanged, double demandScore, double stockPressurePct, double timeFactorMultiplier, String explanation, String statusReason) {
            this.productId = productId;
            this.flavour = flavour;
            this.oldPrice = oldPrice;
            this.newPrice = newPrice;
            this.priceChanged = priceChanged;
            this.demandScore = demandScore;
            this.stockPressurePct = stockPressurePct;
            this.timeFactorMultiplier = timeFactorMultiplier;
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
        public boolean isPriceChanged() { return priceChanged; }
        public void setPriceChanged(boolean priceChanged) { this.priceChanged = priceChanged; }
        public double getDemandScore() { return demandScore; }
        public void setDemandScore(double demandScore) { this.demandScore = demandScore; }
        public double getStockPressurePct() { return stockPressurePct; }
        public void setStockPressurePct(double stockPressurePct) { this.stockPressurePct = stockPressurePct; }
        public double getTimeFactorMultiplier() { return timeFactorMultiplier; }
        public void setTimeFactorMultiplier(double timeFactorMultiplier) { this.timeFactorMultiplier = timeFactorMultiplier; }
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
            private boolean priceChanged;
            private double demandScore;
            private double stockPressurePct;
            private double timeFactorMultiplier;
            private String explanation;
            private String statusReason;

            public PriceEvaluationResultBuilder productId(Long productId) { this.productId = productId; return this; }
            public PriceEvaluationResultBuilder flavour(String flavour) { this.flavour = flavour; return this; }
            public PriceEvaluationResultBuilder oldPrice(BigDecimal oldPrice) { this.oldPrice = oldPrice; return this; }
            public PriceEvaluationResultBuilder newPrice(BigDecimal newPrice) { this.newPrice = newPrice; return this; }
            public PriceEvaluationResultBuilder priceChanged(boolean priceChanged) { this.priceChanged = priceChanged; return this; }
            public PriceEvaluationResultBuilder demandScore(double demandScore) { this.demandScore = demandScore; return this; }
            public PriceEvaluationResultBuilder stockPressurePct(double stockPressurePct) { this.stockPressurePct = stockPressurePct; return this; }
            public PriceEvaluationResultBuilder timeFactorMultiplier(double timeFactorMultiplier) { this.timeFactorMultiplier = timeFactorMultiplier; return this; }
            public PriceEvaluationResultBuilder explanation(String explanation) { this.explanation = explanation; return this; }
            public PriceEvaluationResultBuilder statusReason(String statusReason) { this.statusReason = statusReason; return this; }
            public PriceEvaluationResult build() { return new PriceEvaluationResult(productId, flavour, oldPrice, newPrice, priceChanged, demandScore, stockPressurePct, timeFactorMultiplier, explanation, statusReason); }
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

        // 1. Fetch System Hard Limits
        BigDecimal hardFloor = BigDecimal.valueOf(getConfigDouble("HARD_FLOOR_PRICE", 18.0));
        BigDecimal hardCeiling = BigDecimal.valueOf(getConfigDouble("HARD_CEILING_PRICE", 25.0));

        BigDecimal productMin = product.getMinCupPrice() != null ? product.getMinCupPrice() : hardFloor;
        BigDecimal productMax = product.getMaxCupPrice() != null ? product.getMaxCupPrice() : hardCeiling;

        BigDecimal effectiveMinPrice = productMin.max(hardFloor);
        BigDecimal effectiveMaxPrice = productMax.min(hardCeiling);
        if (effectiveMinPrice.compareTo(effectiveMaxPrice) > 0) {
            effectiveMinPrice = effectiveMaxPrice;
        }

        // 2. Market Crash Overrides
        if (marketCrashService != null && marketCrashService.isCrashActive()) {
            BigDecimal oldPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : effectiveMinPrice;
            BigDecimal crashPrice = effectiveMinPrice;
            boolean changed = oldPrice.compareTo(crashPrice) != 0;

            if (changed) {
                product.setCurrentCupPrice(crashPrice);
                product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
                product.setLastPriceChangeTimestamp(now);
                productRepository.save(product);

                PriceHistory history = PriceHistory.builder()
                        .productId(productId)
                        .oldPrice(oldPrice)
                        .newPrice(crashPrice)
                        .demandScore(0.0)
                        .stockPressurePct(100.0)
                        .timeFactorMultiplier(1.0)
                        .explanation("🚨 Market Crash Event! Price set to floor limit of ₹" + crashPrice)
                        .createdAt(now)
                        .build();
                priceHistoryRepository.save(history);
            }

            return PriceEvaluationResult.builder()
                    .productId(productId)
                    .flavour(product.getFlavour())
                    .oldPrice(oldPrice)
                    .newPrice(crashPrice)
                    .priceChanged(changed)
                    .demandScore(0.0)
                    .stockPressurePct(100.0)
                    .timeFactorMultiplier(1.0)
                    .explanation("🚨 Market Crash Routine Active! Product price held at floor of ₹" + crashPrice)
                    .statusReason("MARKET_CRASH_ACTIVE")
                    .build();
        }

        // 3. Cooldown Check
        long cooldownMins = getConfigLong("cooldown_minutes", 0);
        if (cooldownMins > 0 && product.getLastPriceChangeTimestamp() != null) {
            long minsSinceLastChange = Duration.between(product.getLastPriceChangeTimestamp(), now).toMinutes();
            if (minsSinceLastChange < cooldownMins) {
                String explanation = String.format(
                    "Price maintained at ₹%s for %s. Price change on cooldown (%d/%d mins elapsed).",
                    product.getCurrentCupPrice(), product.getFlavour(), minsSinceLastChange, cooldownMins
                );

                return PriceEvaluationResult.builder()
                        .productId(productId)
                        .flavour(product.getFlavour())
                        .oldPrice(product.getCurrentCupPrice())
                        .newPrice(product.getCurrentCupPrice())
                        .priceChanged(false)
                        .demandScore(50.0)
                        .stockPressurePct(0.0)
                        .timeFactorMultiplier(1.0)
                        .explanation(explanation)
                        .statusReason("COOLDOWN_ACTIVE")
                        .build();
            }
        }

        // 4. Rolling 30-Second Window Order Velocity & Quantity Calculation
        int currentQty = salesOrderItemRepository.countQuantitySoldForProductBetween(productId, now.minusSeconds(30), now);
        int prevQty = salesOrderItemRepository.countQuantitySoldForProductBetween(productId, now.minusSeconds(60), now.minusSeconds(30));

        double demandScore;
        if (currentQty == 0 && prevQty == 0) {
            demandScore = 20.0;
        } else {
            double prevQtyEff = Math.max((double) prevQty, 1.0);
            double velocityRatio = (double) currentQty / prevQtyEff;
            demandScore = Math.max(0.0, Math.min(100.0, 50.0 + ((velocityRatio - 1.0) * 25.0)));
        }

        BigDecimal prevPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : (product.getDefaultCupPrice() != null ? product.getDefaultCupPrice() : new BigDecimal("20.00"));
        BigDecimal targetPrice = prevPrice;

        String actionText;

        if (currentQty > prevQty || (currentQty > 0 && currentQty >= prevQty)) {
            // Surge pricing (+₹1 step)
            targetPrice = prevPrice.add(BigDecimal.ONE).min(effectiveMaxPrice);
            actionText = String.format("Surge Pricing for %s: High order velocity (Current Qty=%d vs Prev=%d, Demand Score %.1f). Price adjusted +₹1 to ₹%s.", product.getFlavour(), currentQty, prevQty, demandScore, targetPrice);
        } else if (currentQty < prevQty || currentQty == 0) {
            // Price decay (-₹1 step)
            targetPrice = prevPrice.subtract(BigDecimal.ONE).max(effectiveMinPrice);
            actionText = String.format("Price Decay for %s: Low/no order velocity (Current Qty=%d vs Prev=%d, Demand Score %.1f). Price adjusted -₹1 to ₹%s.", product.getFlavour(), currentQty, prevQty, demandScore, targetPrice);
        } else {
            // Stable demand
            targetPrice = prevPrice.min(effectiveMaxPrice).max(effectiveMinPrice);
            actionText = String.format("Stable Demand for %s: Steady order velocity (Qty=%d, Demand Score %.1f). Price held at ₹%s.", product.getFlavour(), currentQty, demandScore, targetPrice);
        }

        // Hard bounds clamping
        targetPrice = targetPrice.min(effectiveMaxPrice).max(effectiveMinPrice).setScale(0, java.math.RoundingMode.HALF_UP);

        boolean changed = targetPrice.compareTo(prevPrice) != 0;

        if (changed) {
            product.setCurrentCupPrice(targetPrice);
            product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
            product.setLastPriceChangeTimestamp(now);
            productRepository.save(product);

            PriceHistory history = PriceHistory.builder()
                    .productId(productId)
                    .oldPrice(prevPrice)
                    .newPrice(targetPrice)
                    .demandScore(demandScore)
                    .stockPressurePct(0.0)
                    .timeFactorMultiplier(1.0)
                    .explanation(actionText)
                    .createdAt(now)
                    .build();
            priceHistoryRepository.save(history);
        }

        return PriceEvaluationResult.builder()
                .productId(productId)
                .flavour(product.getFlavour())
                .oldPrice(prevPrice)
                .newPrice(targetPrice)
                .priceChanged(changed)
                .demandScore(demandScore)
                .stockPressurePct(0.0)
                .timeFactorMultiplier(1.0)
                .explanation(actionText)
                .statusReason(changed ? "PRICE_ADJUSTED" : "PRICE_STABLE")
                .build();
    }

    @Transactional
    public List<PriceEvaluationResult> evaluateAllProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream()
                .map(p -> evaluateAndAdjustPrice(p.getId()))
                .toList();
    }

    @Transactional
    public PriceEvaluationResult updateManualPrice(Long productId, BigDecimal newPrice, String reason) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        BigDecimal hardFloor = BigDecimal.valueOf(getConfigDouble("HARD_FLOOR_PRICE", 18.0));
        BigDecimal hardCeiling = BigDecimal.valueOf(getConfigDouble("HARD_CEILING_PRICE", 25.0));

        BigDecimal minPrice = product.getMinCupPrice() != null ? product.getMinCupPrice().max(hardFloor) : hardFloor;
        BigDecimal maxPrice = product.getMaxCupPrice() != null ? product.getMaxCupPrice().min(hardCeiling) : hardCeiling;

        if (newPrice.compareTo(minPrice) < 0) {
            throw new IllegalArgumentException("Price cannot be less than minimum price of ₹" + minPrice);
        }
        if (newPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Price cannot exceed maximum price of ₹" + maxPrice);
        }

        BigDecimal oldPrice = product.getCurrentCupPrice();
        LocalDateTime now = LocalDateTime.now();

        boolean changed = oldPrice == null || oldPrice.compareTo(newPrice) != 0;

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
                    .demandScore(50.0)
                    .stockPressurePct(0.0)
                    .timeFactorMultiplier(1.0)
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
                .priceChanged(changed)
                .demandScore(50.0)
                .stockPressurePct(0.0)
                .timeFactorMultiplier(1.0)
                .explanation("Price manually set to ₹" + newPrice + " (" + reason + ")")
                .statusReason("MANUAL_ADMIN_CHANGE")
                .build();
    }

    public static class AdminPricingDeployRequest {
        private Long productId;
        private BigDecimal defaultPrice;
        private BigDecimal currentPrice;
        private BigDecimal price; // Alias for target price
        private BigDecimal minPrice;
        private BigDecimal maxPrice;

        public AdminPricingDeployRequest() {}
        public AdminPricingDeployRequest(Long productId, BigDecimal defaultPrice, BigDecimal currentPrice, BigDecimal minPrice, BigDecimal maxPrice) {
            this.productId = productId;
            this.defaultPrice = defaultPrice;
            this.currentPrice = currentPrice;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public BigDecimal getDefaultPrice() { return defaultPrice; }
        public void setDefaultPrice(BigDecimal defaultPrice) { this.defaultPrice = defaultPrice; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public BigDecimal getMinPrice() { return minPrice; }
        public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
        public BigDecimal getMaxPrice() { return maxPrice; }
        public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
    }

    @Transactional
    public Product deployAdminPricing(AdminPricingDeployRequest request) {
        if (request == null || request.getProductId() == null) {
            throw new IllegalArgumentException("Product ID is required for deployment.");
        }
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + request.getProductId()));

        BigDecimal minP = request.getMinPrice() != null ? request.getMinPrice() : product.getMinCupPrice();
        BigDecimal maxP = request.getMaxPrice() != null ? request.getMaxPrice() : product.getMaxCupPrice();
        BigDecimal targetP = request.getPrice() != null ? request.getPrice() : request.getCurrentPrice();
        BigDecimal currP = targetP != null ? targetP : (request.getDefaultPrice() != null ? request.getDefaultPrice() : product.getCurrentCupPrice());
        BigDecimal defP = request.getDefaultPrice() != null ? request.getDefaultPrice() : product.getDefaultCupPrice();

        if (minP != null && maxP != null && minP.compareTo(maxP) > 0) {
            throw new IllegalArgumentException("Minimum price (₹" + minP + ") cannot exceed maximum price (₹" + maxP + ")");
        }
        if (currP != null && minP != null && currP.compareTo(minP) < 0) {
            throw new IllegalArgumentException("Current price (₹" + currP + ") cannot be below minimum price (₹" + minP + ")");
        }
        if (currP != null && maxP != null && currP.compareTo(maxP) > 0) {
            throw new IllegalArgumentException("Current price (₹" + currP + ") cannot exceed maximum price (₹" + maxP + ")");
        }

        BigDecimal oldPrice = product.getCurrentCupPrice();

        // 1. Update Product attributes
        product.setMinCupPrice(minP);
        product.setMaxCupPrice(maxP);
        product.setDefaultCupPrice(defP);
        product.setCurrentCupPrice(currP);
        
        // 2. Increment price_version
        product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
        product.setLastPriceChangeTimestamp(LocalDateTime.now());

        Product savedProduct = productRepository.save(product);

        // 3. Update global config bounds if min/max provided
        if (minP != null) {
            systemConfigRepository.findById("HARD_FLOOR_PRICE").ifPresent(cfg -> {
                cfg.setConfigValue(minP.toString());
                systemConfigRepository.save(cfg);
            });
        }
        if (maxP != null) {
            systemConfigRepository.findById("HARD_CEILING_PRICE").ifPresent(cfg -> {
                cfg.setConfigValue(maxP.toString());
                systemConfigRepository.save(cfg);
            });
        }

        // 4. Insert Price History
        PriceHistory history = PriceHistory.builder()
                .productId(product.getId())
                .oldPrice(oldPrice != null ? oldPrice : currP)
                .newPrice(currP)
                .demandScore(50.0)
                .stockPressurePct(0.0)
                .timeFactorMultiplier(1.0)
                .explanation("ATOMIC_ADMIN_DEPLOYMENT: Set default=₹" + defP + ", current=₹" + currP + ", min=₹" + minP + ", max=₹" + maxP)
                .createdAt(LocalDateTime.now())
                .build();
        priceHistoryRepository.save(history);

        return savedProduct;
    }

    private double getConfigDouble(String key, double defaultVal) {
        return systemConfigRepository.findById(key)
                .map(c -> Double.parseDouble(c.getConfigValue()))
                .orElse(defaultVal);
    }

    private long getConfigLong(String key, long defaultVal) {
        return systemConfigRepository.findById(key)
                .map(c -> Long.parseLong(c.getConfigValue()))
                .orElse(defaultVal);
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

        // Stop Market Crash if currently active
        if (marketCrashService != null && marketCrashService.isCrashActive()) {
            marketCrashService.stopMarketCrash();
        }

        List<Product> products = productRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        // 1. Validation phase: check default prices vs min/max bounds
        for (Product p : products) {
            BigDecimal defP = p.getDefaultCupPrice();
            if (defP == null) {
                throw new IllegalStateException("Product " + p.getName() + " has no configured default price");
            }
            BigDecimal minP = p.getMinCupPrice() != null ? p.getMinCupPrice() : new BigDecimal("18.00");
            BigDecimal maxP = p.getMaxCupPrice() != null ? p.getMaxCupPrice() : new BigDecimal("25.00");

            if (defP.compareTo(minP) < 0 || defP.compareTo(maxP) > 0) {
                throw new IllegalArgumentException(String.format("Product %s default price (₹%s) violates price bounds [₹%s, ₹%s]", p.getName(), defP, minP, maxP));
            }
        }

        // 2. Execution phase: reset prices, increment version, log history
        int resetCount = 0;
        for (Product p : products) {
            BigDecimal oldPrice = p.getCurrentCupPrice();
            BigDecimal defP = p.getDefaultCupPrice();

            p.setCurrentCupPrice(defP);
            p.setPriceVersion((p.getPriceVersion() != null ? p.getPriceVersion() : 1) + 1);
            p.setLastPriceChangeTimestamp(now);
            productRepository.save(p);
            resetCount++;

            // Create price history row for tracking
            PriceHistory history = PriceHistory.builder()
                    .productId(p.getId())
                    .oldPrice(oldPrice != null ? oldPrice : defP)
                    .newPrice(defP)
                    .demandScore(50.0)
                    .stockPressurePct(0.0)
                    .timeFactorMultiplier(1.0)
                    .explanation(String.format("ADMIN_RESET_TO_DEFAULT: Reset from ₹%s to default ₹%s. Actor: %s, ReqId: %s", oldPrice, defP, userActor, requestId))
                    .createdAt(now)
                    .build();
            priceHistoryRepository.save(history);
        }

        // 3. Audit trail entry
        if (auditLogRepository != null) {
            AuditLog log = AuditLog.builder()
                    .userId(1L)
                    .action("RESET_ALL_MARKET_PRICES")
                    .module("PRICING")
                    .details(String.format("Reset %d active products to default prices. RequestId: %s, Actor: %s", resetCount, requestId, userActor))
                    .ipAddress("127.0.0.1")
                    .createdAt(now)
                    .build();
            auditLogRepository.save(log);
        }

        List<Product> updatedList = productRepository.findAll();

        return new ResetAllResponse(
                true,
                "All market prices reset to configured default prices successfully",
                resetCount,
                requestId,
                now.toString(),
                updatedList
        );
    }

    @Transactional
    public ResetAllResponse resetAllProductsToDefault() {
        return resetAllProductsToDefault(null, "ADMIN");
    }
}
