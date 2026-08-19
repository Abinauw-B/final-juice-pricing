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

    public PriceAdjustmentService(ProductRepository productRepository, PriceHistoryRepository priceHistoryRepository, SystemConfigRepository systemConfigRepository, SalesOrderItemRepository salesOrderItemRepository, JuiceBatchRepository juiceBatchRepository, DemandCalculationService demandCalculationService, StockPressureService stockPressureService, TimeFactorService timeFactorService, MarketCrashService marketCrashService) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.juiceBatchRepository = juiceBatchRepository;
        this.demandCalculationService = demandCalculationService;
        this.stockPressureService = stockPressureService;
        this.timeFactorService = timeFactorService;
        this.marketCrashService = marketCrashService;
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

        // 4. 30-Second Rolling Window Order Velocity Calculation
        int vt = salesOrderItemRepository.countQuantitySoldForProductBetween(productId, now.minusSeconds(30), now);
        int vtPrev = salesOrderItemRepository.countQuantitySoldForProductBetween(productId, now.minusSeconds(60), now.minusSeconds(30));

        double vtPrevEff = Math.max((double) vtPrev, 1.0);
        double velocityRatio = (double) vt / vtPrevEff;

        // Bounded Demand Score (0 <= demandScore <= 100)
        double demandScore;
        if (vt == 0 && vtPrev == 0) {
            demandScore = 50.0;
        } else {
            demandScore = 50.0 + ((velocityRatio - 1.0) * 25.0);
        }
        demandScore = Math.max(0.0, Math.min(100.0, demandScore));

        BigDecimal prevPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : (product.getDefaultCupPrice() != null ? product.getDefaultCupPrice() : new BigDecimal("20.00"));
        BigDecimal targetPrice = prevPrice;

        String actionText;

        if (vt > vtPrev) {
            // Surge pricing (+₹1 step)
            targetPrice = prevPrice.add(BigDecimal.ONE).min(effectiveMaxPrice);
            actionText = String.format("Surge Pricing: Order velocity increased (Vt=%d vs Vt-1=%d, Demand Score %.1f). Price adjusted +₹1 to ₹%s.", vt, vtPrev, demandScore, targetPrice);
        } else if (vt < vtPrev) {
            // Price decay (-₹1 step)
            targetPrice = prevPrice.subtract(BigDecimal.ONE).max(effectiveMinPrice);
            actionText = String.format("Price Decay: Order velocity decreased (Vt=%d vs Vt-1=%d, Demand Score %.1f). Price adjusted -₹1 to ₹%s.", vt, vtPrev, demandScore, targetPrice);
        } else {
            // Stable demand
            targetPrice = prevPrice.min(effectiveMaxPrice).max(effectiveMinPrice);
            actionText = String.format("Stable Demand: Order velocity steady (Vt=%d, Demand Score %.1f). Price held at ₹%s.", vt, demandScore, targetPrice);
        }

        // Hard bounds clamping
        targetPrice = targetPrice.min(effectiveMaxPrice).max(effectiveMinPrice).setScale(0, java.math.RoundingMode.HALF_UP);

        boolean changed = targetPrice.compareTo(prevPrice) != 0;

        if (changed) {
            product.setCurrentCupPrice(targetPrice);
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
}
