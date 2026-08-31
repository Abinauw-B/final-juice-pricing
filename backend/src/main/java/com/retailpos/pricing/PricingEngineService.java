package com.retailpos.pricing;

import com.retailpos.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PricingEngineService {

    private static final Logger log = LoggerFactory.getLogger(PricingEngineService.class);

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketCrashService marketCrashService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PriceAdjustmentService priceAdjustmentService;
    private final JuiceMarketSettlementRepository settlementRepository;
    private final PricingConfigurationService pricingConfigurationService;

    private static final java.util.concurrent.atomic.AtomicBoolean isSettlementRunning = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static LocalDateTime lastSettlementTime = LocalDateTime.now();
    private static LocalDateTime nextSettlementTime = LocalDateTime.now().plusSeconds(60);

    public PricingEngineService(ProductRepository productRepository,
                                PriceHistoryRepository priceHistoryRepository,
                                MarketCrashService marketCrashService,
                                SimpMessagingTemplate messagingTemplate,
                                RedisTemplate<String, Object> redisTemplate,
                                PriceAdjustmentService priceAdjustmentService,
                                JuiceMarketSettlementRepository settlementRepository,
                                PricingConfigurationService pricingConfigurationService) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.marketCrashService = marketCrashService;
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.priceAdjustmentService = priceAdjustmentService;
        this.settlementRepository = settlementRepository;
        this.pricingConfigurationService = pricingConfigurationService;
    }

    public LocalDateTime getLastSettlementTime() {
        return lastSettlementTime;
    }

    public void resetSettlementTiming(int newIntervalSeconds) {
        int interval = newIntervalSeconds > 0 ? newIntervalSeconds : 60;
        this.nextSettlementTime = LocalDateTime.now().plusSeconds(interval);
    }

    public LocalDateTime getNextSettlementTime() {
        int interval = pricingConfigurationService != null ? pricingConfigurationService.getSettlementIntervalSeconds() : 60;
        if (interval <= 0) interval = 60;
        if (nextSettlementTime == null || LocalDateTime.now().isAfter(nextSettlementTime)) {
            nextSettlementTime = LocalDateTime.now().plusSeconds(interval);
        }
        return nextSettlementTime;
    }

    public static class PriceEvaluationCycleResult {
        private String timestamp;
        private String nextUpdateAt;
        private int evaluatedProductsCount;
        private List<ProductPriceDTO> updatedPrices;
        private String marketStatus;
        private String type = "JUICE_PRICE_UPDATE";

        public PriceEvaluationCycleResult() {}

        public PriceEvaluationCycleResult(String timestamp, String nextUpdateAt, int evaluatedProductsCount, List<ProductPriceDTO> updatedPrices, String marketStatus) {
            this.timestamp = timestamp;
            this.nextUpdateAt = nextUpdateAt;
            this.evaluatedProductsCount = evaluatedProductsCount;
            this.updatedPrices = updatedPrices;
            this.marketStatus = marketStatus;
            this.type = "JUICE_PRICE_UPDATE";
        }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public String getNextUpdateAt() { return nextUpdateAt; }
        public void setNextUpdateAt(String nextUpdateAt) { this.nextUpdateAt = nextUpdateAt; }
        public int getEvaluatedProductsCount() { return evaluatedProductsCount; }
        public void setEvaluatedProductsCount(int evaluatedProductsCount) { this.evaluatedProductsCount = evaluatedProductsCount; }
        public List<ProductPriceDTO> getUpdatedPrices() { return updatedPrices; }
        public void setUpdatedPrices(List<ProductPriceDTO> updatedPrices) { this.updatedPrices = updatedPrices; }
        public String getMarketStatus() { return marketStatus; }
        public void setMarketStatus(String marketStatus) { this.marketStatus = marketStatus; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public static PriceEvaluationCycleResultBuilder builder() { return new PriceEvaluationCycleResultBuilder(); }

        public static class PriceEvaluationCycleResultBuilder {
            private String timestamp;
            private String nextUpdateAt;
            private int evaluatedProductsCount;
            private List<ProductPriceDTO> updatedPrices;
            private String marketStatus;

            public PriceEvaluationCycleResultBuilder timestamp(String timestamp) { this.timestamp = timestamp; return this; }
            public PriceEvaluationCycleResultBuilder nextUpdateAt(String nextUpdateAt) { this.nextUpdateAt = nextUpdateAt; return this; }
            public PriceEvaluationCycleResultBuilder evaluatedProductsCount(int evaluatedProductsCount) { this.evaluatedProductsCount = evaluatedProductsCount; return this; }
            public PriceEvaluationCycleResultBuilder updatedPrices(List<ProductPriceDTO> updatedPrices) { this.updatedPrices = updatedPrices; return this; }
            public PriceEvaluationCycleResultBuilder marketStatus(String marketStatus) { this.marketStatus = marketStatus; return this; }
            public PriceEvaluationCycleResult build() { return new PriceEvaluationCycleResult(timestamp, nextUpdateAt, evaluatedProductsCount, updatedPrices, marketStatus); }
        }
    }

    public static class ProductPriceDTO {
        private Long beverageId;
        private String name;
        private String flavour;
        private BigDecimal currentPrice;
        private BigDecimal effectivePrice;
        private BigDecimal previousPrice;
        private BigDecimal priceDelta;
        private BigDecimal priceChange;
        private Integer priceVersion;
        private double priceChangePct;
        private String trendDirection;
        private double demandRatio;
        private double weightedSales;
        private double targetSales;
        private int rawW0;
        private int rawW1;
        private int rawW2;
        private int unconsumedW0;
        private String demandLevelCategory;
        private boolean isCrashed;
        private BigDecimal minCupPrice;
        private BigDecimal maxCupPrice;
        private Integer orderCount;
        private Integer targetOrders;
        private BigDecimal volatility;
        private String pricingMode;

        public ProductPriceDTO() {}

        public ProductPriceDTO(Long beverageId, String name, String flavour, BigDecimal currentPrice, BigDecimal effectivePrice, BigDecimal previousPrice, BigDecimal priceDelta, BigDecimal priceChange, Integer priceVersion, double priceChangePct, String trendDirection, double demandRatio, double weightedSales, double targetSales, String demandLevelCategory, boolean isCrashed, BigDecimal minCupPrice, BigDecimal maxCupPrice) {
            this.beverageId = beverageId;
            this.name = name;
            this.flavour = flavour;
            this.currentPrice = currentPrice;
            this.effectivePrice = effectivePrice;
            this.previousPrice = previousPrice;
            this.priceDelta = priceDelta;
            this.priceChange = priceChange;
            this.priceVersion = priceVersion;
            this.priceChangePct = priceChangePct;
            this.trendDirection = trendDirection;
            this.demandRatio = demandRatio;
            this.weightedSales = weightedSales;
            this.targetSales = targetSales;
            this.demandLevelCategory = demandLevelCategory;
            this.isCrashed = isCrashed;
            this.minCupPrice = minCupPrice;
            this.maxCupPrice = maxCupPrice;
            this.pricingMode = "DYNAMIC";
        }

        public Long getId() { return beverageId; }
        public void setId(Long id) { this.beverageId = id; }
        public Long getBeverageId() { return beverageId; }
        public void setBeverageId(Long beverageId) { this.beverageId = beverageId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getFlavour() { return flavour; }
        public void setFlavour(String flavour) { this.flavour = flavour; }
        public String getPricingMode() { return pricingMode != null ? pricingMode : "DYNAMIC"; }
        public void setPricingMode(String pricingMode) { this.pricingMode = pricingMode != null ? pricingMode : "DYNAMIC"; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        public BigDecimal getCurrentCupPrice() { return currentPrice; }
        public void setCurrentCupPrice(BigDecimal currentCupPrice) { this.currentPrice = currentCupPrice; }
        public BigDecimal getEffectivePrice() { return effectivePrice; }
        public void setEffectivePrice(BigDecimal effectivePrice) { this.effectivePrice = effectivePrice; }
        public BigDecimal getPreviousPrice() { return previousPrice; }
        public void setPreviousPrice(BigDecimal previousPrice) { this.previousPrice = previousPrice; }
        public BigDecimal getPriceDelta() { return priceDelta; }
        public void setPriceDelta(BigDecimal priceDelta) { this.priceDelta = priceDelta; }
        public BigDecimal getPriceChange() { return priceChange; }
        public void setPriceChange(BigDecimal priceChange) { this.priceChange = priceChange; }
        public Integer getPriceVersion() { return priceVersion; }
        public void setPriceVersion(Integer priceVersion) { this.priceVersion = priceVersion; }
        public double getPriceChangePct() { return priceChangePct; }
        public void setPriceChangePct(double priceChangePct) { this.priceChangePct = priceChangePct; }
        public String getTrendDirection() { return trendDirection; }
        public void setTrendDirection(String trendDirection) { this.trendDirection = trendDirection; }
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
        public double getDemandScore() { return demandRatio * 50.0; }
        public boolean isCrashed() { return isCrashed; }
        public void setCrashed(boolean crashed) { isCrashed = crashed; }
        public BigDecimal getMinCupPrice() { return minCupPrice; }
        public void setMinCupPrice(BigDecimal minCupPrice) { this.minCupPrice = minCupPrice; }
        public BigDecimal getMaxCupPrice() { return maxCupPrice; }
        public void setMaxCupPrice(BigDecimal maxCupPrice) { this.maxCupPrice = maxCupPrice; }
        public Integer getOrderCount() { return orderCount; }
        public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
        public Integer getTargetOrders() { return targetOrders; }
        public void setTargetOrders(Integer targetOrders) { this.targetOrders = targetOrders; }
        public BigDecimal getVolatility() { return volatility; }
        public void setVolatility(BigDecimal volatility) { this.volatility = volatility; }

        public Long getProductId() { return beverageId; }
        public String getProductName() { return name; }
        public double getTargetSalesPerMinute() { return targetSales; }
        public BigDecimal getDeltaPrice() { return priceDelta != null ? priceDelta : (priceChange != null ? priceChange : BigDecimal.ZERO); }
        public String getMovement() { return demandLevelCategory != null ? demandLevelCategory : (priceDelta != null && priceDelta.compareTo(BigDecimal.ZERO) > 0 ? "HIGH_DEMAND" : (priceDelta != null && priceDelta.compareTo(BigDecimal.ZERO) < 0 ? "LOW_DEMAND" : "NORMAL_DEMAND")); }
        public boolean isMarketCrashActive() { return isCrashed; }
        public BigDecimal getDefaultCupPrice() { return previousPrice != null ? previousPrice : currentPrice; }

        public static ProductPriceDTOBuilder builder() { return new ProductPriceDTOBuilder(); }

        public static class ProductPriceDTOBuilder {
            private Long beverageId;
            private String name;
            private String flavour;
            private BigDecimal currentPrice;
            private BigDecimal effectivePrice;
            private BigDecimal previousPrice;
            private BigDecimal priceDelta;
            private BigDecimal priceChange;
            private Integer priceVersion;
            private double priceChangePct;
            private String trendDirection;
            private double demandRatio;
            private double weightedSales;
            private double targetSales;
            private int rawW0;
            private int rawW1;
            private int rawW2;
            private int unconsumedW0;
            private String demandLevelCategory;
            private boolean isCrashed;
            private BigDecimal minCupPrice;
            private BigDecimal maxCupPrice;
            private Integer orderCount;
            private Integer targetOrders;
            private BigDecimal volatility;
            private String pricingMode;

            public ProductPriceDTOBuilder beverageId(Long beverageId) { this.beverageId = beverageId; return this; }
            public ProductPriceDTOBuilder name(String name) { this.name = name; return this; }
            public ProductPriceDTOBuilder flavour(String flavour) { this.flavour = flavour; return this; }
            public ProductPriceDTOBuilder pricingMode(String pricingMode) { this.pricingMode = pricingMode; return this; }
            public ProductPriceDTOBuilder currentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; return this; }
            public ProductPriceDTOBuilder effectivePrice(BigDecimal effectivePrice) { this.effectivePrice = effectivePrice; return this; }
            public ProductPriceDTOBuilder previousPrice(BigDecimal previousPrice) { this.previousPrice = previousPrice; return this; }
            public ProductPriceDTOBuilder priceDelta(BigDecimal priceDelta) { this.priceDelta = priceDelta; return this; }
            public ProductPriceDTOBuilder priceChange(BigDecimal priceChange) { this.priceChange = priceChange; return this; }
            public ProductPriceDTOBuilder priceVersion(Integer priceVersion) { this.priceVersion = priceVersion; return this; }
            public ProductPriceDTOBuilder priceChangePct(double priceChangePct) { this.priceChangePct = priceChangePct; return this; }
            public ProductPriceDTOBuilder trendDirection(String trendDirection) { this.trendDirection = trendDirection; return this; }
            public ProductPriceDTOBuilder demandRatio(double demandRatio) { this.demandRatio = demandRatio; return this; }
            public ProductPriceDTOBuilder weightedSales(double weightedSales) { this.weightedSales = weightedSales; return this; }
            public ProductPriceDTOBuilder targetSales(double targetSales) { this.targetSales = targetSales; return this; }
            public ProductPriceDTOBuilder rawW0(int rawW0) { this.rawW0 = rawW0; return this; }
            public ProductPriceDTOBuilder rawW1(int rawW1) { this.rawW1 = rawW1; return this; }
            public ProductPriceDTOBuilder rawW2(int rawW2) { this.rawW2 = rawW2; return this; }
            public ProductPriceDTOBuilder unconsumedW0(int unconsumedW0) { this.unconsumedW0 = unconsumedW0; return this; }
            public ProductPriceDTOBuilder demandLevelCategory(String demandLevelCategory) { this.demandLevelCategory = demandLevelCategory; return this; }
            public ProductPriceDTOBuilder isCrashed(boolean isCrashed) { this.isCrashed = isCrashed; return this; }
            public ProductPriceDTOBuilder minCupPrice(BigDecimal minCupPrice) { this.minCupPrice = minCupPrice; return this; }
            public ProductPriceDTOBuilder maxCupPrice(BigDecimal maxCupPrice) { this.maxCupPrice = maxCupPrice; return this; }
            public ProductPriceDTOBuilder orderCount(Integer orderCount) { this.orderCount = orderCount; return this; }
            public ProductPriceDTOBuilder targetOrders(Integer targetOrders) { this.targetOrders = targetOrders; return this; }
            public ProductPriceDTOBuilder volatility(BigDecimal volatility) { this.volatility = volatility; return this; }

            public ProductPriceDTO build() {
                ProductPriceDTO dto = new ProductPriceDTO(beverageId, name, flavour, currentPrice, effectivePrice, previousPrice, priceDelta, priceChange, priceVersion, priceChangePct, trendDirection, demandRatio, weightedSales, targetSales, demandLevelCategory, isCrashed, minCupPrice, maxCupPrice);
                dto.setPricingMode(pricingMode);
                dto.setRawW0(rawW0);
                dto.setRawW1(rawW1);
                dto.setRawW2(rawW2);
                dto.setUnconsumedW0(unconsumedW0);
                dto.setOrderCount(orderCount);
                dto.setTargetOrders(targetOrders);
                dto.setVolatility(volatility);
                return dto;
            }
        }
    }

    /**
     * DWMA Pricing Settlement Cycle
     */
    @Transactional
    public PriceEvaluationCycleResult executeSettlementCycle() {
        return executeSettlementCycle(false);
    }

    @Transactional
    public PriceEvaluationCycleResult executeSettlementForProducts(Set<Long> productIds) {
        if (productIds != null && !productIds.isEmpty()) {
            for (Long productId : productIds) {
                priceAdjustmentService.evaluateAndAdjustPrice(productId);
            }
        }
        broadcastCurrentState();
        return getCurrentMarketState();
    }

    @Transactional
    public PriceEvaluationCycleResult executeSettlementCycle(boolean force) {
        return executeSettlementCycle(force, LocalDateTime.now());
    }

    @Transactional
    public PriceEvaluationCycleResult executeSettlementCycle(boolean force, LocalDateTime evaluationTime) {
        if (!isSettlementRunning.compareAndSet(false, true)) {
            log.warn("Previous settlement cycle still running. Skipping concurrent trigger.");
            return getCurrentMarketState();
        }
        try {
            LocalDateTime now = evaluationTime != null ? evaluationTime : LocalDateTime.now();
            int intervalSeconds = pricingConfigurationService != null ? pricingConfigurationService.getSettlementIntervalSeconds() : 60;
            log.info("⚡ Running Dynamic Juice Exchange Settlement Cycle (interval={}s) at {} (force={})...", intervalSeconds, now, force);

            long epochSeconds = now.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
            long bucket = (epochSeconds / intervalSeconds) * intervalSeconds;
            String windowStartKey = force ? "SETTLEMENT_FORCE_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 4)
                    : "SETTLEMENT_" + intervalSeconds + "_" + bucket;

            if (!force && settlementRepository.existsByIdempotencyKey(windowStartKey)) {
                log.info("Settlement for window {} already executed. Skipping duplicate run.", windowStartKey);
                return getCurrentMarketState();
            }

            lastSettlementTime = now;
            nextSettlementTime = now.plusSeconds(intervalSeconds);

            String currentStatus = "OPEN";
            if (PriceAdjustmentService.isMarketPaused()) {
                currentStatus = "PAUSED";
            } else if (marketCrashService.isCrashActive()) {
                currentStatus = "CRASH";
            }

            List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
            List<ProductPriceDTO> dtoList = new ArrayList<>();

            long startMs = System.currentTimeMillis();
            for (Product product : products) {
                BigDecimal oldPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : product.getDefaultCupPrice();

                PriceAdjustmentService.PriceEvaluationResult evalResult = priceAdjustmentService.evaluateAndAdjustPrice(product.getId(), now);

                Product reloadedProduct = productRepository.findById(product.getId()).orElse(product);
                BigDecimal newPrice = evalResult.getNewPrice() != null ? evalResult.getNewPrice() : reloadedProduct.getCurrentCupPrice();
                BigDecimal basePrice = reloadedProduct.getDefaultCupPrice() != null ? reloadedProduct.getDefaultCupPrice() : BigDecimal.valueOf(25);
                BigDecimal effectivePrice = marketCrashService.getEffectivePrice(reloadedProduct);
                BigDecimal priceDelta = evalResult.getPriceChange() != null ? evalResult.getPriceChange() : newPrice.subtract(oldPrice);
                double changePct = (basePrice.doubleValue() > 0) ? ((newPrice.subtract(basePrice)).doubleValue() / basePrice.doubleValue()) * 100.0 : 0.0;
                String trendDirection = priceDelta.compareTo(BigDecimal.ZERO) > 0 ? "UP" : (priceDelta.compareTo(BigDecimal.ZERO) < 0 ? "DOWN" : "FLAT");

                boolean crashed = marketCrashService.isProductCrashed(reloadedProduct.getId());

                ProductPriceDTO dto = ProductPriceDTO.builder()
                        .beverageId(reloadedProduct.getId())
                        .name(reloadedProduct.getName())
                        .flavour(reloadedProduct.getFlavour())
                        .currentPrice(newPrice)
                        .effectivePrice(effectivePrice)
                        .previousPrice(oldPrice)
                        .priceDelta(priceDelta)
                        .priceChange(priceDelta)
                        .priceVersion(reloadedProduct.getPriceVersion() != null ? reloadedProduct.getPriceVersion() : 1)
                        .priceChangePct(BigDecimal.valueOf(changePct).setScale(1, RoundingMode.HALF_UP).doubleValue())
                        .trendDirection(trendDirection)
                        .demandRatio(evalResult.getDemandRatio())
                        .weightedSales(evalResult.getWeightedSales())
                        .targetSales(evalResult.getTargetSales())
                        .rawW0(evalResult.getRawW0())
                        .rawW1(evalResult.getRawW1())
                        .rawW2(evalResult.getRawW2())
                        .unconsumedW0(evalResult.getUnconsumedW0())
                        .demandLevelCategory(evalResult.getDemandLevelCategory())
                        .isCrashed(crashed)
                        .minCupPrice(reloadedProduct.getMinCupPrice())
                        .maxCupPrice(reloadedProduct.getMaxCupPrice())
                        .build();

                dtoList.add(dto);

                try {
                    if (redisTemplate != null) {
                        redisTemplate.opsForValue().set("live_price:" + reloadedProduct.getId(), dto);
                    }
                } catch (Exception e) {
                    log.debug("Redis write bypassed: {}", e.getMessage());
                }
            }

            long durationMs = System.currentTimeMillis() - startMs;
            log.info("✅ Dynamic DWMA Settlement Cycle completed in {}ms. Evaluated {} products.", durationMs, dtoList.size());

            JuiceMarketSettlement settlement = JuiceMarketSettlement.builder()
                    .settlementWindowStart(now.minusSeconds(intervalSeconds))
                    .settlementWindowEnd(now)
                    .idempotencyKey(windowStartKey)
                    .status("COMPLETED")
                    .createdAt(now)
                    .build();
            settlementRepository.save(settlement);

            PriceEvaluationCycleResult cycleResult = PriceEvaluationCycleResult.builder()
                    .timestamp(now.toString())
                    .nextUpdateAt(nextSettlementTime.toString())
                    .evaluatedProductsCount(dtoList.size())
                    .updatedPrices(dtoList)
                    .marketStatus(currentStatus)
                    .build();

            try {
                if (messagingTemplate != null) {
                    messagingTemplate.convertAndSend("/topic/prices", cycleResult);
                    messagingTemplate.convertAndSend("/topic/settlement", cycleResult);
                    messagingTemplate.convertAndSend("/topic/products", productRepository.findByIsActiveTrueOrderByIdAsc());
                    messagingTemplate.convertAndSend("/topic/led-display", cycleResult);
                    for (ProductPriceDTO dto : dtoList) {
                        log.info("[STOMP] topic=/topic/prices productId={} price={}", dto.getBeverageId(), dto.getCurrentPrice());
                    }
                }
            } catch (Exception e) {
                log.debug("WebSocket broadcast bypass: {}", e.getMessage());
            }

            return cycleResult;
        } finally {
            isSettlementRunning.set(false);
        }
    }

    public PriceEvaluationCycleResult getCurrentMarketState() {
        LocalDateTime now = LocalDateTime.now();
        List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
        List<ProductPriceDTO> dtoList = new ArrayList<>();

        String currentStatus = "OPEN";
        if (PriceAdjustmentService.isMarketPaused()) {
            currentStatus = "PAUSED";
        } else if (marketCrashService.isCrashActive()) {
            currentStatus = "CRASH";
        }

        for (Product product : products) {
            BigDecimal currentPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : product.getDefaultCupPrice();
            BigDecimal effectivePrice = marketCrashService.getEffectivePrice(product);

            Optional<PriceHistory> lastHist = priceHistoryRepository.findFirstByProductIdOrderByCreatedAtDesc(product.getId());
            BigDecimal oldPrice = lastHist.isPresent() && lastHist.get().getOldPrice() != null ? lastHist.get().getOldPrice() : currentPrice;
            BigDecimal delta = currentPrice.subtract(oldPrice);
            String trend = delta.compareTo(BigDecimal.ZERO) > 0 ? "UP" : (delta.compareTo(BigDecimal.ZERO) < 0 ? "DOWN" : "FLAT");

            boolean crashed = marketCrashService.isProductCrashed(product.getId());

            BigDecimal baseP = product.getDefaultCupPrice() != null ? product.getDefaultCupPrice() : BigDecimal.valueOf(25);
            double changePct = (baseP.doubleValue() > 0) ? ((currentPrice.subtract(baseP)).doubleValue() / baseP.doubleValue()) * 100.0 : 0.0;

            ProductPriceDTO dto = ProductPriceDTO.builder()
                    .beverageId(product.getId())
                    .name(product.getName())
                    .flavour(product.getFlavour())
                    .currentPrice(currentPrice)
                    .effectivePrice(effectivePrice)
                    .previousPrice(oldPrice)
                    .priceDelta(delta)
                    .priceChangePct(BigDecimal.valueOf(changePct).setScale(1, RoundingMode.HALF_UP).doubleValue())
                    .trendDirection(trend)
                    .demandRatio(1.0)
                    .weightedSales(1.0)
                    .targetSales(product.getTargetSalesPer1Minute() != null ? product.getTargetSalesPer1Minute() : 0.55)
                    .demandLevelCategory("NORMAL")
                    .isCrashed(crashed)
                    .minCupPrice(product.getMinCupPrice())
                    .maxCupPrice(product.getMaxCupPrice())
                    .build();

            dtoList.add(dto);
        }

        return PriceEvaluationCycleResult.builder()
                .timestamp(now.toString())
                .nextUpdateAt(getNextSettlementTime().toString())
                .evaluatedProductsCount(dtoList.size())
                .updatedPrices(dtoList)
                .marketStatus(currentStatus)
                .build();
    }

    public void broadcastCurrentState() {
        try {
            if (messagingTemplate != null) {
                PriceEvaluationCycleResult state = getCurrentMarketState();
                messagingTemplate.convertAndSend("/topic/prices", state);
                messagingTemplate.convertAndSend("/topic/products", productRepository.findByIsActiveTrueOrderByIdAsc());
                messagingTemplate.convertAndSend("/topic/led-display", state);
            }
        } catch (Exception e) {
            log.debug("WebSocket broadcast bypass: {}", e.getMessage());
        }
    }

    public PriceEvaluationCycleResult execute60SecondPricingEngine() {
        return executeSettlementCycle();
    }
}
