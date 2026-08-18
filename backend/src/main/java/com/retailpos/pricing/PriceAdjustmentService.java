package com.retailpos.pricing;

import com.retailpos.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class PriceAdjustmentService {

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final DemandCalculationService demandCalculationService;
    private final StockPressureService stockPressureService;
    private final TimeFactorService timeFactorService;
    private final MarketCrashService marketCrashService;

    public PriceAdjustmentService(ProductRepository productRepository, PriceHistoryRepository priceHistoryRepository, SystemConfigRepository systemConfigRepository, DemandCalculationService demandCalculationService, StockPressureService stockPressureService, TimeFactorService timeFactorService, MarketCrashService marketCrashService) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.systemConfigRepository = systemConfigRepository;
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
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        if (marketCrashService != null && marketCrashService.isCrashActive()) {
            return PriceEvaluationResult.builder()
                    .productId(productId)
                    .flavour(product.getFlavour())
                    .oldPrice(product.getMinCupPrice())
                    .newPrice(product.getMinCupPrice())
                    .priceChanged(false)
                    .demandScore(0.0)
                    .stockPressurePct(0.0)
                    .timeFactorMultiplier(1.0)
                    .explanation("🚨 Market Crash Routine Active! All products set to floor price.")
                    .statusReason("MARKET_CRASH_ACTIVE")
                    .build();
        }

        // Read Config weights
        double wVelocity = getConfigDouble("weight_velocity", 0.40);
        double wStock = getConfigDouble("weight_stock_pressure", 0.40);
        double wTime = getConfigDouble("weight_time_factor", 0.20);
        long cooldownMins = getConfigLong("cooldown_minutes", 0);

        LocalDateTime now = LocalDateTime.now();
        LocalTime currentTime = now.toLocalTime();

        // Check Cooldown only if enabled (> 0)
        if (cooldownMins > 0 && product.getLastPriceChangeTimestamp() != null) {
            long minsSinceLastChange = Duration.between(product.getLastPriceChangeTimestamp(), now).toMinutes();
            if (minsSinceLastChange < cooldownMins) {
                double stockPct = stockPressureService.calculateStockPressurePercentage(productId);
                double timeMult = timeFactorService.getTimeFactorMultiplier(currentTime);
                double demandScore = demandCalculationService.calculateDemandScore(productId, wVelocity, wStock, wTime, currentTime);

                String explanation = String.format(
                    "Price maintained at ₹%s for %s. Price change on cooldown (%d/%d mins elapsed). Demand Score: %.1f.",
                    product.getCurrentCupPrice(), product.getFlavour(), minsSinceLastChange, cooldownMins, demandScore
                );

                return PriceEvaluationResult.builder()
                        .productId(productId)
                        .flavour(product.getFlavour())
                        .oldPrice(product.getCurrentCupPrice())
                        .newPrice(product.getCurrentCupPrice())
                        .priceChanged(false)
                        .demandScore(demandScore)
                        .stockPressurePct(stockPct)
                        .timeFactorMultiplier(timeMult)
                        .explanation(explanation)
                        .statusReason("COOLDOWN_ACTIVE")
                        .build();
            }
        }

        double stockPct = stockPressureService.calculateStockPressurePercentage(productId);
        double timeMult = timeFactorService.getTimeFactorMultiplier(currentTime);
        double demandScore = demandCalculationService.calculateDemandScore(productId, wVelocity, wStock, wTime, currentTime);

        BigDecimal currentPrice = product.getCurrentCupPrice();
        BigDecimal minPrice = product.getMinCupPrice();
        BigDecimal maxPrice = product.getMaxCupPrice();

        BigDecimal targetPrice = currentPrice;
        boolean changed = false;
        String actionText;

        if (demandScore >= 65.0) {
            if (currentPrice.compareTo(maxPrice) < 0) {
                targetPrice = currentPrice.add(BigDecimal.ONE); // + ₹1 step
                changed = true;
                actionText = String.format("Increased price for %s by ₹1 to ₹%s due to HIGH DEMAND (Score: %.1f, Stock Pressure: %.1f%%).", product.getFlavour(), targetPrice, demandScore, stockPct);
            } else {
                actionText = String.format("High demand detected for %s (Score: %.1f), but price is already capped at MAX limit of ₹%s.", product.getFlavour(), demandScore, maxPrice);
            }
        } else if (demandScore <= 35.0) {
            if (currentPrice.compareTo(minPrice) > 0) {
                targetPrice = currentPrice.subtract(BigDecimal.ONE); // - ₹1 step
                changed = true;
                actionText = String.format("Decreased price for %s by ₹1 to ₹%s due to LOW DEMAND (Score: %.1f, Stock Pressure: %.1f%%).", product.getFlavour(), targetPrice, demandScore, stockPct);
            } else {
                actionText = String.format("Low demand detected for %s (Score: %.1f), but price is already bounded at MIN floor of ₹%s.", product.getFlavour(), demandScore, minPrice);
            }
        } else {
            actionText = String.format("Price maintained at ₹%s for %s. Demand is steady (Score: %.1f, Stock Pressure: %.1f%%).", currentPrice, product.getFlavour(), demandScore, stockPct);
        }

        if (changed) {
            product.setCurrentCupPrice(targetPrice);
            product.setLastPriceChangeTimestamp(now);
            productRepository.save(product);

            PriceHistory history = PriceHistory.builder()
                    .productId(productId)
                    .oldPrice(currentPrice)
                    .newPrice(targetPrice)
                    .demandScore(demandScore)
                    .stockPressurePct(stockPct)
                    .timeFactorMultiplier(timeMult)
                    .explanation(actionText)
                    .createdAt(now)
                    .build();
            priceHistoryRepository.save(history);
        }

        return PriceEvaluationResult.builder()
                .productId(productId)
                .flavour(product.getFlavour())
                .oldPrice(currentPrice)
                .newPrice(targetPrice)
                .priceChanged(changed)
                .demandScore(demandScore)
                .stockPressurePct(stockPct)
                .timeFactorMultiplier(timeMult)
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

        BigDecimal minPrice = product.getMinCupPrice() != null ? product.getMinCupPrice() : new BigDecimal("18.00");
        BigDecimal maxPrice = product.getMaxCupPrice() != null ? product.getMaxCupPrice() : new BigDecimal("25.00");

        if (newPrice.compareTo(minPrice) < 0) {
            throw new IllegalArgumentException("Price cannot be less than minimum price of ₹" + minPrice);
        }
        if (newPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Price cannot exceed maximum price of ₹" + maxPrice);
        }

        BigDecimal oldPrice = product.getCurrentCupPrice();
        LocalDateTime now = LocalDateTime.now();

        product.setCurrentCupPrice(newPrice);
        product.setLastPriceChangeTimestamp(now);
        productRepository.save(product);

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

        return PriceEvaluationResult.builder()
                .productId(productId)
                .flavour(product.getFlavour())
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .priceChanged(true)
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
