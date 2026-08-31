package com.retailpos.pricing;

import com.retailpos.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PriceAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(PriceAdjustmentService.class);

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final MarketCrashService marketCrashService;
    private final PricingProcessedSaleRepository pricingProcessedSaleRepository;
    private final com.retailpos.pricing.redis.PricingRedisRepository redisRepository;
    private final PricingConfigurationService pricingConfigurationService;

    private static LocalDateTime marketStartTime = LocalDateTime.now();
    private static boolean marketPaused = false;

    public PriceAdjustmentService(ProductRepository productRepository,
                                  PriceHistoryRepository priceHistoryRepository,
                                  SalesOrderItemRepository salesOrderItemRepository,
                                  MarketCrashService marketCrashService,
                                  PricingProcessedSaleRepository pricingProcessedSaleRepository,
                                  com.retailpos.pricing.redis.PricingRedisRepository redisRepository,
                                  PricingConfigurationService pricingConfigurationService) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.marketCrashService = marketCrashService;
        this.pricingProcessedSaleRepository = pricingProcessedSaleRepository;
        this.redisRepository = redisRepository;
        this.pricingConfigurationService = pricingConfigurationService;
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

    public static LocalDateTime getMarketStartTime() {
        return marketStartTime;
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
        private int rawW0;
        private int rawW1;
        private int rawW2;
        private int unconsumedW0;
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
        public int getRawW0() { return rawW0; }
        public void setRawW0(int rawW0) { this.rawW0 = rawW0; }
        public int getRawW1() { return rawW1; }
        public void setRawW1(int rawW1) { this.rawW1 = rawW1; }
        public int getRawW2() { return rawW2; }
        public void setRawW2(int rawW2) { this.rawW2 = rawW2; }
        public int getUnconsumedW0() { return unconsumedW0; }
        public void setUnconsumedW0(int unconsumedW0) { this.unconsumedW0 = unconsumedW0; }
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
            private int rawW0;
            private int rawW1;
            private int rawW2;
            private int unconsumedW0;
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
            public PriceEvaluationResultBuilder rawW0(int rawW0) { this.rawW0 = rawW0; return this; }
            public PriceEvaluationResultBuilder rawW1(int rawW1) { this.rawW1 = rawW1; return this; }
            public PriceEvaluationResultBuilder rawW2(int rawW2) { this.rawW2 = rawW2; return this; }
            public PriceEvaluationResultBuilder unconsumedW0(int unconsumedW0) { this.unconsumedW0 = unconsumedW0; return this; }
            public PriceEvaluationResultBuilder demandLevelCategory(String demandLevelCategory) { this.demandLevelCategory = demandLevelCategory; return this; }
            public PriceEvaluationResultBuilder explanation(String explanation) { this.explanation = explanation; return this; }
            public PriceEvaluationResultBuilder statusReason(String statusReason) { this.statusReason = statusReason; return this; }

            public PriceEvaluationResult build() {
                PriceEvaluationResult r = new PriceEvaluationResult(productId, flavour, oldPrice, newPrice, priceChange, priceChanged, demandRatio, weightedSales, targetSales, demandLevelCategory, explanation, statusReason);
                r.setRawW0(rawW0);
                r.setRawW1(rawW1);
                r.setRawW2(rawW2);
                r.setUnconsumedW0(unconsumedW0);
                return r;
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

        if (product.getCurrentCupPrice() == null && product.getDefaultCupPrice() == null) {
            throw new IllegalArgumentException("Product currentCupPrice is required for product ID: " + productId);
        }
        if (product.getMinCupPrice() == null) {
            throw new IllegalArgumentException("Product minCupPrice (floor) is required for product ID: " + productId);
        }
        if (product.getMaxCupPrice() == null) {
            throw new IllegalArgumentException("Product maxCupPrice (ceiling) is required for product ID: " + productId);
        }
        int targetOrders = (product.getTargetOrders() != null && product.getTargetOrders() > 0) ? product.getTargetOrders() : 5;
        BigDecimal volatility = (product.getVolatility() != null) ? product.getVolatility() : new BigDecimal("1.00");
        BigDecimal oldPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : product.getDefaultCupPrice();
        BigDecimal floor = (product.getMinCupPrice() != null)
                ? product.getMinCupPrice()
                : (pricingConfigurationService != null && pricingConfigurationService.getMinCupPrice() != null ? pricingConfigurationService.getMinCupPrice() : new BigDecimal("18.00"));
        BigDecimal ceiling = (product.getMaxCupPrice() != null)
                ? product.getMaxCupPrice()
                : (pricingConfigurationService != null && pricingConfigurationService.getMaxCupPrice() != null ? pricingConfigurationService.getMaxCupPrice() : new BigDecimal("35.00"));
        int orderCount = product.getOrderCount() != null ? product.getOrderCount() : 0;

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
                    .weightedSales((double) orderCount)
                    .targetSales((double) targetOrders)
                    .demandLevelCategory("NORMAL")
                    .explanation("Exchange is currently PAUSED by Admin. Prices held stable.")
                    .statusReason("MARKET_PAUSED")
                    .build();
        }

        // Check Market Crash
        if (marketCrashService != null && marketCrashService.isCrashActive()) {
            BigDecimal crashPrice = marketCrashService.calculateCrashPrice(product);
            BigDecimal priceChange = crashPrice.subtract(oldPrice);
            boolean changed = oldPrice.compareTo(crashPrice) != 0;

            product.setCurrentCupPrice(crashPrice);
            if (changed) {
                product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
                product.setLastPriceChangeTimestamp(now);
            }
            // DO NOT reset order_count during crash!
            productRepository.saveAndFlush(product);

            PriceHistory history = PriceHistory.builder()
                    .productId(productId)
                    .oldPrice(oldPrice)
                    .newPrice(crashPrice)
                    .priceChange(priceChange)
                    .demandRatio(1.0)
                    .orderCount(orderCount)
                    .rawPriceChangePercent(BigDecimal.ZERO)
                    .appliedPriceChangePercent(BigDecimal.ZERO)
                    .volatility(volatility)
                    .floorPrice(floor)
                    .ceilingPrice(ceiling)
                    .priceVersion(product.getPriceVersion())
                    .triggerType("MARKET_CRASH_ROUND")
                    .reason("MARKET_CRASH_HOLD")
                    .explanation("🚨 Market crash active: Price set to floor + 5% (₹" + crashPrice + ")")
                    .createdAt(now)
                    .build();
            priceHistoryRepository.save(history);

            return PriceEvaluationResult.builder()
                    .productId(productId)
                    .flavour(product.getFlavour())
                    .oldPrice(oldPrice)
                    .newPrice(crashPrice)
                    .priceChange(priceChange)
                    .priceChanged(changed)
                    .demandRatio(1.0)
                    .weightedSales((double) orderCount)
                    .targetSales((double) targetOrders)
                    .rawW0(orderCount)
                    .demandLevelCategory("CRASH")
                    .explanation("Market Crash Active: Price held at ₹" + crashPrice)
                    .statusReason("MARKET_CRASH_HOLD")
                    .build();
        }

        // Check Explicit Admin Manual Override Mode
        if ("MANUAL_OVERRIDE".equalsIgnoreCase(product.getPricingMode())) {
            log.info("[DWMA SETTLEMENT] ProductId={} ({}) is in MANUAL_OVERRIDE mode. Price held constant at ₹{}.",
                    productId, product.getName(), oldPrice);
            return PriceEvaluationResult.builder()
                    .productId(productId)
                    .flavour(product.getFlavour())
                    .oldPrice(oldPrice)
                    .newPrice(oldPrice)
                    .priceChange(BigDecimal.ZERO)
                    .priceChanged(false)
                    .demandRatio(1.0)
                    .weightedSales(1.0)
                    .targetSales(1.0)
                    .rawW0(orderCount)
                    .demandLevelCategory("MANUAL_OVERRIDE")
                    .explanation("Product is in MANUAL_OVERRIDE mode. Price held constant at ₹" + oldPrice + " by Admin.")
                    .statusReason("MANUAL_OVERRIDE_HOLD")
                    .build();
        }

        // --- SINGLE AUTHORITATIVE DWMA PRICING MODEL (DYNAMIC CONFIGURATION) ---
        long configVersion = pricingConfigurationService != null ? pricingConfigurationService.getConfigurationVersion() : 1L;
        BigDecimal weightW0 = pricingConfigurationService != null ? pricingConfigurationService.getWeightW0() : new BigDecimal("1.0000");
        BigDecimal weightW1 = pricingConfigurationService != null ? pricingConfigurationService.getWeightW1() : new BigDecimal("0.5000");
        BigDecimal weightW2 = pricingConfigurationService != null ? pricingConfigurationService.getWeightW2() : new BigDecimal("0.2500");
        BigDecimal highThresh = pricingConfigurationService != null ? pricingConfigurationService.getHighDemandThreshold() : new BigDecimal("1.1000");
        BigDecimal stableLow = pricingConfigurationService != null ? pricingConfigurationService.getStableDemandLowerThreshold() : new BigDecimal("0.9000");
        BigDecimal lowThresh = pricingConfigurationService != null ? pricingConfigurationService.getLowDemandThreshold() : new BigDecimal("0.5000");
        BigDecimal incStep = pricingConfigurationService != null ? pricingConfigurationService.getIncreaseStep() : new BigDecimal("1.00");
        BigDecimal decStep1 = pricingConfigurationService != null ? pricingConfigurationService.getDecreaseStep1() : new BigDecimal("1.00");

        // 1. Time windows based on configured settlement interval: W0 [now - interval, now), W1 [now - 2*interval, now - interval), W2 [now - 3*interval, now - 2*interval)
        int intervalSec = pricingConfigurationService != null ? pricingConfigurationService.getSettlementIntervalSeconds() : 60;
        LocalDateTime marketStart = getMarketStartTime();

        LocalDateTime w0Start = now.minusSeconds(intervalSec);
        if (marketStart != null && w0Start.isBefore(marketStart)) {
            w0Start = marketStart;
        }
        int w0 = salesOrderItemRepository.countQuantitySoldForProductBetweenExclusiveEnd(productId, w0Start, now);

        LocalDateTime w1Start = now.minusSeconds(2L * intervalSec);
        LocalDateTime w1End = now.minusSeconds(intervalSec);
        int w1 = 0;
        if (marketStart == null || w1End.isAfter(marketStart)) {
            if (marketStart != null && w1Start.isBefore(marketStart)) {
                w1Start = marketStart;
            }
            w1 = salesOrderItemRepository.countQuantitySoldForProductBetweenExclusiveEnd(productId, w1Start, w1End);
        }

        LocalDateTime w2Start = now.minusSeconds(3L * intervalSec);
        LocalDateTime w2End = now.minusSeconds(2L * intervalSec);
        int w2 = 0;
        if (marketStart == null || w2End.isAfter(marketStart)) {
            if (marketStart != null && w2Start.isBefore(marketStart)) {
                w2Start = marketStart;
            }
            w2 = salesOrderItemRepository.countQuantitySoldForProductBetweenExclusiveEnd(productId, w2Start, w2End);
        }

        // 2. Weighted sales: S_w = (weightW0 * W0) + (weightW1 * W1) + (weightW2 * W2)
        BigDecimal sw = BigDecimal.valueOf(w0).multiply(weightW0)
                .add(BigDecimal.valueOf(w1).multiply(weightW1))
                .add(BigDecimal.valueOf(w2).multiply(weightW2))
                .setScale(2, RoundingMode.HALF_UP);
        double weightedSales = sw.doubleValue();

        // 3. Target sales normalized to intervalSec:
        // Product target is defined per 1 minute (60 seconds). Normalized target for interval = targetPer1Min * (intervalSec / 60.0)
        double baseTargetPer1Min = pricingConfigurationService != null
                ? pricingConfigurationService.getTargetSalesForProduct(product)
                : (product.getTargetSalesPer1Minute() != null && product.getTargetSalesPer1Minute() > 0 ? product.getTargetSalesPer1Minute() : 0.55);
        double normalizedTarget = baseTargetPer1Min * ((double) intervalSec / 60.0);
        BigDecimal targetSalesBd = BigDecimal.valueOf(normalizedTarget).setScale(4, RoundingMode.HALF_UP);

        // 4. Demand ratio: R_d = S_w / TargetSales
        BigDecimal rd = (targetSalesBd.compareTo(BigDecimal.ZERO) > 0)
                ? sw.divide(targetSalesBd, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        double demandRatio = rd.doubleValue();

        // 5. Dynamic movement rules (Strictly +₹1, ₹0, -₹1)
        BigDecimal deltaP;
        int movement;
        String reason;
        String demandLevelCategory;

        if (rd.compareTo(highThresh) >= 0) {
            if (w0 > 0) {
                deltaP = incStep;
                movement = incStep.intValue();
                reason = "HIGH_DEMAND_SURGE";
                demandLevelCategory = "HIGH";
            } else {
                deltaP = BigDecimal.ZERO;
                movement = 0;
                reason = "HIGH_HISTORICAL_ZERO_CURRENT_HOLD";
                demandLevelCategory = "NORMAL";
            }
        } else if (rd.compareTo(stableLow) >= 0) {
            deltaP = BigDecimal.ZERO;
            movement = 0;
            reason = "STABLE_DEMAND";
            demandLevelCategory = "NORMAL";
        } else {
            deltaP = decStep1.negate();
            movement = decStep1.negate().intValue();
            reason = (rd.compareTo(lowThresh) >= 0) ? "BELOW_NORMAL_DEMAND_DECAY" : "ZERO_DEMAND_DECAY";
            demandLevelCategory = (rd.compareTo(lowThresh) >= 0) ? "LOW" : "VERY_LOW";
        }

        // Validate maximum ₹1.00 price movement per normal settlement
        PricingConfigurationService.validatePriceMovement(deltaP);

        // 6. Bounded price: MAX(minCupPrice, MIN(maxCupPrice, oldPrice + deltaP))
        BigDecimal uncappedPrice = oldPrice.add(deltaP);
        BigDecimal newPrice = uncappedPrice.max(floor).min(ceiling).setScale(2, RoundingMode.HALF_UP);
        BigDecimal priceChange = newPrice.subtract(oldPrice);
        boolean changed = oldPrice.compareTo(newPrice) != 0;

        String intervalLabel = pricingConfigurationService != null ? pricingConfigurationService.getSettlementIntervalLabel() : (intervalSec + "s");
        String settlementKey = "SETTLEMENT_" + now.withSecond(0).withNano(0).toString();

        log.info("[PRICING_SETTLEMENT_START] product='{}' (id={}) window={} (interval={}s) oldPrice=₹{}", product.getName(), productId, intervalLabel, intervalSec, oldPrice);
        log.info("[PRODUCT_DEMAND_CALCULATED] product='{}' w0={} w1={} w2={} weightedSales={} target={} demandRatio={}",
                product.getName(), w0, w1, w2, weightedSales, normalizedTarget, demandRatio);
        log.info("[PRICE_MOVEMENT_CALCULATED] product='{}' category={} rawDelta={}",
                product.getName(), demandLevelCategory, deltaP);
        log.info("[PRICE_CLAMPED] product='{}' uncapped=₹{} floor=₹{} ceiling=₹{} clamped=₹{}",
                product.getName(), uncappedPrice, floor, ceiling, newPrice);

        String explanation = String.format(
                "DWMA %s Settlement: W0=%d, W1=%d, W2=%d | S_w=%.2f, Target=%.2f cups (%s), R_d=%.4f => Movement %+d. Price: ₹%s -> ₹%s (%s) [v%d]",
                intervalLabel, w0, w1, w2, weightedSales, normalizedTarget, intervalLabel, demandRatio, movement, oldPrice, newPrice, reason, configVersion
        );

        // Update product state
        product.setCurrentCupPrice(newPrice);
        if (changed) {
            product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
            product.setLastPriceChangeTimestamp(now);
        }
        productRepository.saveAndFlush(product);
        log.info("[PRICE_PERSISTED] product='{}' newPrice=₹{} priceVersion={}", product.getName(), newPrice, product.getPriceVersion());

        if (redisRepository != null) {
            redisRepository.setProductPrice(productId, newPrice);
            log.info("[REDIS_SYNCED] product='{}' price=₹{}", product.getName(), newPrice);
        }

        // Save authoritative audit record in PriceHistory
        PriceHistory history = PriceHistory.builder()
                .productId(productId)
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .priceChange(priceChange)
                .demandRatio(demandRatio)
                .weightedSales(weightedSales)
                .targetSales(normalizedTarget)
                .rawW0(w0)
                .rawW1(w1)
                .rawW2(w2)
                .unconsumedW0(0)
                .orderCount(w0)
                .rawPriceChangePercent(BigDecimal.ZERO)
                .appliedPriceChangePercent(BigDecimal.ZERO)
                .volatility(product.getVolatility())
                .floorPrice(floor)
                .ceilingPrice(ceiling)
                .priceVersion(product.getPriceVersion())
                .configVersion(configVersion)
                .triggerType("SCHEDULED_ROUND")
                .settlementId(settlementKey)
                .calculationWindowStart(now.minusSeconds(intervalSec))
                .calculationWindowEnd(now)
                .reason(reason)
                .explanation(explanation)
                .createdAt(now)
                .build();
        priceHistoryRepository.save(history);

        log.info("[PRICING_SETTLEMENT_COMPLETE] product='{}' oldPrice=₹{} newPrice=₹{} mode={}",
                product.getName(), oldPrice, newPrice, product.getPricingMode());

        return PriceEvaluationResult.builder()
                .productId(productId)
                .flavour(product.getFlavour())
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .priceChange(priceChange)
                .priceChanged(changed)
                .demandRatio(demandRatio)
                .weightedSales(weightedSales)
                .targetSales(normalizedTarget)
                .rawW0(w0)
                .rawW1(w1)
                .rawW2(w2)
                .demandLevelCategory(demandLevelCategory)
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

        if (product.getMinCupPrice() == null) {
            throw new IllegalArgumentException("Product minCupPrice (floor) is required for product ID: " + productId);
        }
        if (product.getMaxCupPrice() == null) {
            throw new IllegalArgumentException("Product maxCupPrice (ceiling) is required for product ID: " + productId);
        }

        BigDecimal floor = product.getMinCupPrice();
        BigDecimal ceiling = product.getMaxCupPrice();

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
        product.setPricingMode("MANUAL_OVERRIDE");
        if (changed) {
            product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
        }
        product.setLastPriceChangeTimestamp(now);
        productRepository.saveAndFlush(product);

        if (redisRepository != null) {
            redisRepository.setProductPrice(productId, newPrice);
        }

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
                    .reason("MANUAL_PRICE_OVERRIDE")
                    .explanation(reason != null && !reason.isBlank() ? reason : "Manual price override set by Admin to ₹" + newPrice)
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
                .demandLevelCategory("MANUAL_OVERRIDE")
                .explanation("Price manually set to ₹" + newPrice + " (MANUAL_OVERRIDE lock active)")
                .statusReason("MANUAL_PRICE_OVERRIDE")
                .build();
    }

    @Transactional
    public PriceEvaluationResult releaseManualOverride(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        BigDecimal currentPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : product.getDefaultCupPrice();
        LocalDateTime now = LocalDateTime.now();

        product.setPricingMode("DYNAMIC");
        product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
        product.setLastPriceChangeTimestamp(now);
        productRepository.saveAndFlush(product);

        if (redisRepository != null) {
            redisRepository.setProductPrice(productId, currentPrice);
        }

        PriceHistory history = PriceHistory.builder()
                .productId(productId)
                .oldPrice(currentPrice)
                .newPrice(currentPrice)
                .priceChange(BigDecimal.ZERO)
                .demandRatio(1.0)
                .weightedSales(1.0)
                .targetSales(1.0)
                .calculationWindowStart(now)
                .calculationWindowEnd(now)
                .reason("RELEASE_MANUAL_OVERRIDE")
                .explanation("Manual price override released. Resumed DYNAMIC DWMA pricing starting from ₹" + currentPrice)
                .createdAt(now)
                .build();
        priceHistoryRepository.save(history);

        log.info("[MANUAL OVERRIDE RELEASED] ProductId={} ({}) returned to DYNAMIC mode at current price ₹{}.",
                productId, product.getName(), currentPrice);

        return PriceEvaluationResult.builder()
                .productId(productId)
                .flavour(product.getFlavour())
                .oldPrice(currentPrice)
                .newPrice(currentPrice)
                .priceChange(BigDecimal.ZERO)
                .priceChanged(false)
                .demandRatio(1.0)
                .weightedSales(1.0)
                .targetSales(1.0)
                .demandLevelCategory("NORMAL")
                .explanation("Manual override released. Resumed DYNAMIC pricing at ₹" + currentPrice)
                .statusReason("RELEASE_MANUAL_OVERRIDE")
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

        pricingProcessedSaleRepository.deleteAllInBatch();
        List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
        LocalDateTime now = LocalDateTime.now();
        resetMarketStartTime();

        int resetCount = 0;
        BigDecimal basePrice = pricingConfigurationService != null ? pricingConfigurationService.getDefaultCupPrice() : new BigDecimal("25.00");
        BigDecimal minPrice = pricingConfigurationService != null ? pricingConfigurationService.getMinCupPrice() : new BigDecimal("18.00");
        BigDecimal maxPrice = pricingConfigurationService != null ? pricingConfigurationService.getMaxCupPrice() : new BigDecimal("35.00");

        for (Product p : products) {
            BigDecimal oldPrice = p.getCurrentCupPrice();

            p.setCurrentCupPrice(basePrice);
            p.setDefaultCupPrice(basePrice);
            p.setMinCupPrice(minPrice);
            p.setMaxCupPrice(maxPrice);
            p.setPricingMode("DYNAMIC");
            p.setPriceVersion((p.getPriceVersion() != null ? p.getPriceVersion() : 1) + 1);
            p.setLastPriceChangeTimestamp(null);
            p.setOrderCount(0);
            productRepository.saveAndFlush(p);
            if (redisRepository != null) {
                redisRepository.setProductPrice(p.getId(), basePrice);
            }
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
                    .reason("ADMIN_RESET_TO_DEFAULT")
                    .explanation(String.format("ADMIN_RESET_TO_DEFAULT: Reset from ₹%s to base ₹%s. Actor: %s", oldPrice, basePrice, userActor))
                    .createdAt(now)
                    .build();
            priceHistoryRepository.save(history);
        }

        List<Product> updatedList = productRepository.findByIsActiveTrueOrderByIdAsc();

        return new ResetAllResponse(
                true,
                "All market prices reset to base ₹" + basePrice + " successfully",
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
        private BigDecimal defaultCupPrice;
        private Double targetSales;
        private Double targetSalesPer1Minute;
        private Double targetSalesPer2Minute;
        private BigDecimal volatility;

        public AdminPricingDeployRequest() {}
        public AdminPricingDeployRequest(Long productId, String flavour, BigDecimal currentCupPrice, BigDecimal minCupPrice, BigDecimal maxCupPrice, Double targetSalesPer1Minute) {
            this.productId = productId;
            this.flavour = flavour;
            this.currentCupPrice = currentCupPrice;
            this.minCupPrice = minCupPrice;
            this.maxCupPrice = maxCupPrice;
            this.targetSalesPer1Minute = targetSalesPer1Minute;
            this.targetSales = targetSalesPer1Minute;
            this.targetSalesPer2Minute = targetSalesPer1Minute != null ? targetSalesPer1Minute * 2.0 : null;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getFlavour() { return flavour; }
        public void setFlavour(String flavour) { this.flavour = flavour; }
        public String getPricingMode() { return pricingMode; }
        public void setPricingMode(String pricingMode) { this.pricingMode = pricingMode; }
        private String pricingMode;
        public BigDecimal getCurrentCupPrice() { return currentCupPrice; }
        public void setCurrentCupPrice(BigDecimal currentCupPrice) { this.currentCupPrice = currentCupPrice; }
        public BigDecimal getCurrentPrice() { return currentCupPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentCupPrice = currentPrice; }
        public void setPrice(BigDecimal price) { this.currentCupPrice = price; }
        public BigDecimal getMinCupPrice() { return minCupPrice; }
        public void setMinCupPrice(BigDecimal minCupPrice) { this.minCupPrice = minCupPrice; }
        public void setMinPrice(BigDecimal minPrice) { this.minCupPrice = minPrice; }
        public BigDecimal getMaxCupPrice() { return maxCupPrice; }
        public void setMaxCupPrice(BigDecimal maxCupPrice) { this.maxCupPrice = maxCupPrice; }
        public void setMaxPrice(BigDecimal maxPrice) { this.maxCupPrice = maxPrice; }
        public BigDecimal getDefaultCupPrice() { return defaultCupPrice; }
        public void setDefaultCupPrice(BigDecimal defaultCupPrice) { this.defaultCupPrice = defaultCupPrice; }
        public void setDefaultPrice(BigDecimal defaultPrice) { this.defaultCupPrice = defaultPrice; }
        public Double getTargetSales() { return targetSales != null ? targetSales : (targetSalesPer1Minute != null ? targetSalesPer1Minute : (targetSalesPer2Minute != null ? targetSalesPer2Minute / 2.0 : null)); }
        public void setTargetSales(Double targetSales) {
            this.targetSales = targetSales;
            this.targetSalesPer1Minute = targetSales;
            if (targetSales != null) this.targetSalesPer2Minute = targetSales * 2.0;
        }
        public Double getTargetSalesPer1Minute() { return targetSalesPer1Minute != null ? targetSalesPer1Minute : targetSales; }
        public void setTargetSalesPer1Minute(Double targetSalesPer1Minute) {
            this.targetSalesPer1Minute = targetSalesPer1Minute;
            this.targetSales = targetSalesPer1Minute;
            if (targetSalesPer1Minute != null) this.targetSalesPer2Minute = targetSalesPer1Minute * 2.0;
        }
        public Double getTargetSalesPer2Minute() { return targetSalesPer2Minute; }
        public void setTargetSalesPer2Minute(Double targetSalesPer2Minute) {
            this.targetSalesPer2Minute = targetSalesPer2Minute;
            if (targetSalesPer2Minute != null) {
                this.targetSalesPer1Minute = targetSalesPer2Minute / 2.0;
                this.targetSales = this.targetSalesPer1Minute;
            }
        }
        public BigDecimal getVolatility() { return volatility; }
        public void setVolatility(BigDecimal volatility) { this.volatility = volatility; }
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
        if (request.getDefaultCupPrice() != null) product.setDefaultCupPrice(request.getDefaultCupPrice());
        if (request.getTargetSalesPer1Minute() != null) {
            product.setTargetSalesPer1Minute(request.getTargetSalesPer1Minute());
        } else if (request.getTargetSales() != null) {
            product.setTargetSalesPer1Minute(request.getTargetSales());
        } else if (request.getTargetSalesPer2Minute() != null) {
            product.setTargetSalesPer1Minute(request.getTargetSalesPer2Minute() / 2.0);
        }
        if (newPrice != null) product.setCurrentCupPrice(newPrice);
        if (request.getVolatility() != null) product.setVolatility(request.getVolatility());
        if (request.getPricingMode() != null && !request.getPricingMode().isBlank()) {
            product.setPricingMode(request.getPricingMode());
        }

        product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
        product.setLastPriceChangeTimestamp(LocalDateTime.now());

        Product saved = productRepository.saveAndFlush(product);
        if (redisRepository != null) {
            redisRepository.setProductPrice(saved.getId(), saved.getCurrentCupPrice());
        }

        BigDecimal effectiveNewPrice = newPrice != null ? newPrice : (oldPrice != null ? oldPrice : BigDecimal.ZERO);
        BigDecimal effectiveOldPrice = oldPrice != null ? oldPrice : effectiveNewPrice;

        PriceHistory history = PriceHistory.builder()
                .productId(saved.getId())
                .oldPrice(effectiveOldPrice)
                .newPrice(effectiveNewPrice)
                .priceChange(effectiveNewPrice.subtract(effectiveOldPrice))
                .reason("ADMIN_DEPLOY")
                .explanation("Admin deployed product config updates.")
                .configVersion(pricingConfigurationService != null ? pricingConfigurationService.getConfigurationVersion() : 1L)
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
        private Integer priceVersion;

        public PriceDebugDTO() {}

        public PriceDebugDTO(Long productId, String productName, String flavour, BigDecimal currentPrice, double targetSales, int windowW0, int windowW1, int windowW2, double weightedSales, double demandRatio, String demandLevelCategory, int priceMovement, BigDecimal projectedNewPrice, BigDecimal minPrice, BigDecimal maxPrice, String calculationBreakdown, Integer priceVersion) {
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
            this.priceVersion = priceVersion;
        }

        public Integer getPriceVersion() { return priceVersion; }
        public void setPriceVersion(Integer priceVersion) { this.priceVersion = priceVersion; }

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
        public int getW0() { return windowW0; }
        public int getWindowW1() { return windowW1; }
        public void setWindowW1(int windowW1) { this.windowW1 = windowW1; }
        public int getW1() { return windowW1; }
        public int getWindowW2() { return windowW2; }
        public void setWindowW2(int windowW2) { this.windowW2 = windowW2; }
        public int getW2() { return windowW2; }
        public double getWeightedSales() { return weightedSales; }
        public void setWeightedSales(double weightedSales) { this.weightedSales = weightedSales; }
        public double getDemandRatio() { return demandRatio; }
        public void setDemandRatio(double demandRatio) { this.demandRatio = demandRatio; }
        public String getDemandLevelCategory() { return demandLevelCategory; }
        public void setDemandLevelCategory(String demandLevelCategory) { this.demandLevelCategory = demandLevelCategory; }
        public String getDemandLevel() { return demandLevelCategory; }
        public int getPriceMovement() { return priceMovement; }
        public void setPriceMovement(int priceMovement) { this.priceMovement = priceMovement; }
        public int getMovement() { return priceMovement; }
        public BigDecimal getProjectedNewPrice() { return projectedNewPrice; }
        public void setProjectedNewPrice(BigDecimal projectedNewPrice) { this.projectedNewPrice = projectedNewPrice; }
        public BigDecimal getProjectedPrice() { return projectedNewPrice; }
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
        if (p.getMinCupPrice() == null) {
            throw new IllegalArgumentException("Product minCupPrice (floor) is required for product ID: " + productId);
        }
        if (p.getMaxCupPrice() == null) {
            throw new IllegalArgumentException("Product maxCupPrice (ceiling) is required for product ID: " + productId);
        }

        BigDecimal currentPrice = p.getCurrentCupPrice() != null ? p.getCurrentCupPrice() : p.getDefaultCupPrice();
        BigDecimal floor = (p.getMinCupPrice() != null)
                ? p.getMinCupPrice()
                : (pricingConfigurationService != null && pricingConfigurationService.getMinCupPrice() != null ? pricingConfigurationService.getMinCupPrice() : new BigDecimal("18.00"));
        BigDecimal ceiling = (p.getMaxCupPrice() != null)
                ? p.getMaxCupPrice()
                : (pricingConfigurationService != null && pricingConfigurationService.getMaxCupPrice() != null ? pricingConfigurationService.getMaxCupPrice() : new BigDecimal("35.00"));

        BigDecimal weightW0 = pricingConfigurationService != null ? pricingConfigurationService.getWeightW0() : new BigDecimal("1.0000");
        BigDecimal weightW1 = pricingConfigurationService != null ? pricingConfigurationService.getWeightW1() : new BigDecimal("0.5000");
        BigDecimal weightW2 = pricingConfigurationService != null ? pricingConfigurationService.getWeightW2() : new BigDecimal("0.2500");
        BigDecimal highThresh = pricingConfigurationService != null ? pricingConfigurationService.getHighDemandThreshold() : new BigDecimal("1.1000");
        BigDecimal stableLow = pricingConfigurationService != null ? pricingConfigurationService.getStableDemandLowerThreshold() : new BigDecimal("0.9000");
        BigDecimal lowThresh = pricingConfigurationService != null ? pricingConfigurationService.getLowDemandThreshold() : new BigDecimal("0.5000");
        BigDecimal incStep = pricingConfigurationService != null ? pricingConfigurationService.getIncreaseStep() : new BigDecimal("1.00");
        BigDecimal decStep1 = pricingConfigurationService != null ? pricingConfigurationService.getDecreaseStep1() : new BigDecimal("1.00");

        double targetSales = pricingConfigurationService != null
                ? pricingConfigurationService.getTargetSalesForProduct(p)
                : (p.getTargetSalesPer1Minute() != null && p.getTargetSalesPer1Minute() > 0 ? p.getTargetSalesPer1Minute() : 0.55);

        // 1-minute DWMA windows
        int w0 = salesOrderItemRepository.countQuantitySoldForProductBetweenExclusiveEnd(productId, now.minusMinutes(1), now);
        int w1 = salesOrderItemRepository.countQuantitySoldForProductBetweenExclusiveEnd(productId, now.minusMinutes(2), now.minusMinutes(1));
        int w2 = salesOrderItemRepository.countQuantitySoldForProductBetweenExclusiveEnd(productId, now.minusMinutes(3), now.minusMinutes(2));

        BigDecimal sw = BigDecimal.valueOf(w0).multiply(weightW0)
                .add(BigDecimal.valueOf(w1).multiply(weightW1))
                .add(BigDecimal.valueOf(w2).multiply(weightW2))
                .setScale(2, RoundingMode.HALF_UP);
        double weightedSales = sw.doubleValue();

        BigDecimal targetSalesBd = BigDecimal.valueOf(targetSales).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rd = (targetSalesBd.compareTo(BigDecimal.ZERO) > 0)
                ? sw.divide(targetSalesBd, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        double demandRatio = rd.doubleValue();

        int movement;
        String category;
        if (rd.compareTo(highThresh) >= 0) {
            if (w0 > 0) {
                movement = incStep.intValue();
                category = "HIGH";
            } else {
                movement = 0;
                category = "NORMAL";
            }
        } else if (rd.compareTo(stableLow) >= 0) {
            movement = 0;
            category = "NORMAL";
        } else {
            movement = decStep1.negate().intValue();
            category = (rd.compareTo(lowThresh) >= 0) ? "LOW" : "VERY_LOW";
        }

        BigDecimal uncappedPrice = currentPrice.add(BigDecimal.valueOf(movement));
        BigDecimal projectedPrice = uncappedPrice.max(floor).min(ceiling).setScale(2, RoundingMode.HALF_UP);

        String breakdown = String.format(
                "Current Window W0 [0–1m]: %d, W1 [1–2m]: %d, W2 [2–3m]: %d | Weighted Sales: %.2f*%d + %.2f*%d + %.2f*%d = %.2f | Target: %.2f cups/min | Demand Ratio: %.2f / %.2f = %.4f (%s) | Movement: %+d => Projected: ₹%s",
                w0, w1, w2, weightW0.doubleValue(), w0, weightW1.doubleValue(), w1, weightW2.doubleValue(), w2, weightedSales, targetSales, weightedSales, targetSales, demandRatio, category, movement, projectedPrice
        );

        return new PriceDebugDTO(
                p.getId(), p.getName(), p.getFlavour(), currentPrice, targetSales,
                w0, w1, w2, weightedSales, demandRatio, category, movement, projectedPrice, floor, ceiling, breakdown,
                p.getPriceVersion() != null ? p.getPriceVersion() : 1
        );
    }

    public List<PriceDebugDTO> getDebugEvaluationAll() {
        List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
        return products.stream().map(p -> getDebugEvaluation(p.getId())).toList();
    }
}
