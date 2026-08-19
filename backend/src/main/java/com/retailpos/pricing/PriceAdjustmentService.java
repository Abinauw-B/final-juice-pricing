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

        LocalDateTime now = LocalDateTime.now();

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
                        .demandScore(1.0)
                        .stockPressurePct(0.0)
                        .timeFactorMultiplier(1.0)
                        .explanation(explanation)
                        .statusReason("COOLDOWN_ACTIVE")
                        .build();
            }
        }

        // 1. Demand Pressure (weighted sales windows: 15m, 1h, 6h, 24h)
        int s15m = salesOrderItemRepository.countQuantitySoldForProductSince(productId, now.minusMinutes(15));
        int s1h = salesOrderItemRepository.countQuantitySoldForProductSince(productId, now.minusHours(1));
        int s6h = salesOrderItemRepository.countQuantitySoldForProductSince(productId, now.minusHours(6));
        int s24h = salesOrderItemRepository.countQuantitySoldForProductSince(productId, now.minusHours(24));

        double recentDemandUnits = (s15m * 0.50) + (s1h * 0.30) + (s6h * 0.15) + (s24h * 0.05);
        double demandScore = (s15m == 0 && s1h == 0 && s6h == 0 && s24h == 0) ? 1.0 : 1.0 + (recentDemandUnits / 5.0);

        double demandPressure;
        if (demandScore <= 0.5) {
            demandPressure = -0.10 + (demandScore / 0.5) * 0.05;
        } else if (demandScore <= 1.0) {
            demandPressure = -0.05 + ((demandScore - 0.5) / 0.5) * 0.05;
        } else if (demandScore <= 1.5) {
            demandPressure = 0.00 + ((demandScore - 1.0) / 0.5) * 0.08;
        } else {
            demandPressure = 0.08 + Math.min(1.0, (demandScore - 1.5) / 0.5) * 0.07;
        }
        demandPressure = Math.max(-0.10, Math.min(0.20, demandPressure));

        // 2. Inventory Pressure (currentStock / targetStock)
        Optional<JuiceBatch> activeBatchOpt = juiceBatchRepository.findFirstActiveBatchForProduct(productId);
        double inventoryRatio = 1.0;
        if (activeBatchOpt.isPresent()) {
            JuiceBatch b = activeBatchOpt.get();
            if (b.getInitialVolumeMl() > 0) {
                inventoryRatio = (double) b.getRemainingVolumeMl() / b.getInitialVolumeMl();
            }
        }
        double inventoryPressure;
        if (inventoryRatio > 1.50) {
            inventoryPressure = -0.10;
        } else if (inventoryRatio >= 1.00) {
            inventoryPressure = -0.05;
        } else if (inventoryRatio >= 0.75) {
            inventoryPressure = 0.00;
        } else if (inventoryRatio >= 0.50) {
            inventoryPressure = 0.04;
        } else if (inventoryRatio >= 0.25) {
            inventoryPressure = 0.08;
        } else if (inventoryRatio >= 0.10) {
            inventoryPressure = 0.12;
        } else {
            inventoryPressure = 0.15;
        }
        inventoryPressure = Math.max(-0.10, Math.min(0.15, inventoryPressure));

        // 3. Trend Pressure (current vs previous velocity)
        int prevVel = salesOrderItemRepository.countQuantitySoldForProductBetween(productId, now.minusMinutes(30), now.minusMinutes(15));
        double trendRatio = prevVel > 0 ? (double) s15m / prevVel : (s15m > 0 ? 1.5 : 1.0);
        double trendPressure;
        if (trendRatio >= 1.5) {
            trendPressure = 0.10;
        } else if (trendRatio >= 1.1) {
            trendPressure = 0.05;
        } else if (trendRatio >= 0.9) {
            trendPressure = 0.00;
        } else if (trendRatio >= 0.5) {
            trendPressure = -0.05;
        } else {
            trendPressure = -0.10;
        }
        trendPressure = Math.max(-0.10, Math.min(0.10, trendPressure));

        // 4. Time Pressure
        int hour = now.getHour();
        double timePressure;
        if (hour >= 16 && hour < 18) {
            timePressure = -0.10; // Happy hour
        } else if (hour >= 20 && hour < 23) {
            timePressure = 0.08; // Peak hours
        } else if (hour >= 23 || hour < 1) {
            timePressure = -0.05; // Late hours
        } else {
            timePressure = 0.00; // Normal
        }

        // Config Weights (0.40, 0.30, 0.20, 0.10)
        double wDemand = getConfigDouble("weight_velocity", 0.40);
        double wInventory = getConfigDouble("weight_stock_pressure", 0.30);
        double wTrend = getConfigDouble("weight_trend", 0.20);
        double wTime = getConfigDouble("weight_time_factor", 0.10);

        double totalPressure = (demandPressure * wDemand)
                             + (inventoryPressure * wInventory)
                             + (trendPressure * wTrend)
                             + (timePressure * wTime);

        BigDecimal basePrice = product.getDefaultCupPrice() != null ? product.getDefaultCupPrice() : new BigDecimal("20.00");
        BigDecimal rawPrice = basePrice.multiply(BigDecimal.valueOf(1.0 + totalPressure));

        BigDecimal prevPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : basePrice;

        // Smoothing: smoothedPrice = (prevPrice * 0.70) + (rawPrice * 0.30)
        BigDecimal smoothedPrice = prevPrice.multiply(new BigDecimal("0.70"))
                                    .add(rawPrice.multiply(new BigDecimal("0.30")));

        // Movement Limit (max ±5% step per cycle or ±₹1 step)
        BigDecimal maxStepUp = prevPrice.add(BigDecimal.ONE);
        BigDecimal maxStepDown = prevPrice.subtract(BigDecimal.ONE);
        BigDecimal movementLimitedPrice = smoothedPrice.min(maxStepUp).max(maxStepDown);

        // Min/Max Clamp
        BigDecimal minPrice = product.getMinCupPrice() != null ? product.getMinCupPrice() : basePrice.multiply(new BigDecimal("0.70"));
        BigDecimal maxPrice = product.getMaxCupPrice() != null ? product.getMaxCupPrice() : basePrice.multiply(new BigDecimal("2.00"));

        BigDecimal targetPrice = movementLimitedPrice.min(maxPrice).max(minPrice).setScale(0, java.math.RoundingMode.HALF_UP);

        boolean changed = targetPrice.compareTo(prevPrice) != 0;
        String actionText;

        if (changed) {
            if (targetPrice.compareTo(prevPrice) > 0) {
                actionText = String.format("Price increased from ₹%s to ₹%s for %s due to market pressure (Total Pressure: %.2f%%).", prevPrice, targetPrice, product.getFlavour(), totalPressure * 100.0);
            } else {
                actionText = String.format("Price decreased from ₹%s to ₹%s for %s due to market pressure (Total Pressure: %.2f%%).", prevPrice, targetPrice, product.getFlavour(), totalPressure * 100.0);
            }

            product.setCurrentCupPrice(targetPrice);
            product.setLastPriceChangeTimestamp(now);
            productRepository.save(product);

            PriceHistory history = PriceHistory.builder()
                    .productId(productId)
                    .oldPrice(prevPrice)
                    .newPrice(targetPrice)
                    .demandScore(demandScore)
                    .stockPressurePct((1.0 - inventoryRatio) * 100.0)
                    .timeFactorMultiplier(1.0 + timePressure)
                    .explanation(actionText)
                    .createdAt(now)
                    .build();
            priceHistoryRepository.save(history);
        } else {
            actionText = String.format("Price maintained at ₹%s for %s. Market pressure is steady (Total Pressure: %.2f%%).", prevPrice, product.getFlavour(), totalPressure * 100.0);
        }

        return PriceEvaluationResult.builder()
                .productId(productId)
                .flavour(product.getFlavour())
                .oldPrice(prevPrice)
                .newPrice(targetPrice)
                .priceChanged(changed)
                .demandScore(demandScore)
                .stockPressurePct((1.0 - inventoryRatio) * 100.0)
                .timeFactorMultiplier(1.0 + timePressure)
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
