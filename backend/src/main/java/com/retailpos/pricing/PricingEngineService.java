package com.retailpos.pricing;

import com.retailpos.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final DemandCalculationService demandCalculationService;
    private final StockPressureService stockPressureService;
    private final TimeFactorService timeFactorService;

    public PricingEngineService(ProductRepository productRepository, PriceHistoryRepository priceHistoryRepository, MarketCrashService marketCrashService, SimpMessagingTemplate messagingTemplate, RedisTemplate<String, Object> redisTemplate, PriceAdjustmentService priceAdjustmentService, DemandCalculationService demandCalculationService, StockPressureService stockPressureService, TimeFactorService timeFactorService) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.marketCrashService = marketCrashService;
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.priceAdjustmentService = priceAdjustmentService;
        this.demandCalculationService = demandCalculationService;
        this.stockPressureService = stockPressureService;
        this.timeFactorService = timeFactorService;
    }

    public static class PriceEvaluationCycleResult {
        private String timestamp;
        private int evaluatedProductsCount;
        private List<ProductPriceDTO> updatedPrices;
        private String marketStatus;

        public PriceEvaluationCycleResult() {}
        public PriceEvaluationCycleResult(String timestamp, int evaluatedProductsCount, List<ProductPriceDTO> updatedPrices, String marketStatus) {
            this.timestamp = timestamp;
            this.evaluatedProductsCount = evaluatedProductsCount;
            this.updatedPrices = updatedPrices;
            this.marketStatus = marketStatus;
        }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public int getEvaluatedProductsCount() { return evaluatedProductsCount; }
        public void setEvaluatedProductsCount(int evaluatedProductsCount) { this.evaluatedProductsCount = evaluatedProductsCount; }
        public List<ProductPriceDTO> getUpdatedPrices() { return updatedPrices; }
        public void setUpdatedPrices(List<ProductPriceDTO> updatedPrices) { this.updatedPrices = updatedPrices; }
        public String getMarketStatus() { return marketStatus; }
        public void setMarketStatus(String marketStatus) { this.marketStatus = marketStatus; }

        public static PriceEvaluationCycleResultBuilder builder() { return new PriceEvaluationCycleResultBuilder(); }
        public static class PriceEvaluationCycleResultBuilder {
            private String timestamp;
            private int evaluatedProductsCount;
            private List<ProductPriceDTO> updatedPrices;
            private String marketStatus;

            public PriceEvaluationCycleResultBuilder timestamp(String timestamp) { this.timestamp = timestamp; return this; }
            public PriceEvaluationCycleResultBuilder evaluatedProductsCount(int evaluatedProductsCount) { this.evaluatedProductsCount = evaluatedProductsCount; return this; }
            public PriceEvaluationCycleResultBuilder updatedPrices(List<ProductPriceDTO> updatedPrices) { this.updatedPrices = updatedPrices; return this; }
            public PriceEvaluationCycleResultBuilder marketStatus(String marketStatus) { this.marketStatus = marketStatus; return this; }
            public PriceEvaluationCycleResult build() { return new PriceEvaluationCycleResult(timestamp, evaluatedProductsCount, updatedPrices, marketStatus); }
        }
    }

    public static class ProductPriceDTO {
        private Long beverageId;
        private String name;
        private String flavour;
        private BigDecimal currentPrice;
        private BigDecimal previousPrice;
        private BigDecimal priceDelta;
        private double priceChangePct;
        private String trendDirection;
        private double demandScore;
        private double velocityScore;
        private double stockPressureScore;
        private double timeDecayScore;

        public ProductPriceDTO() {}
        public ProductPriceDTO(Long beverageId, String name, String flavour, BigDecimal currentPrice, BigDecimal previousPrice, BigDecimal priceDelta, double priceChangePct, String trendDirection, double demandScore, double velocityScore, double stockPressureScore, double timeDecayScore) {
            this.beverageId = beverageId;
            this.name = name;
            this.flavour = flavour;
            this.currentPrice = currentPrice;
            this.previousPrice = previousPrice;
            this.priceDelta = priceDelta;
            this.priceChangePct = priceChangePct;
            this.trendDirection = trendDirection;
            this.demandScore = demandScore;
            this.velocityScore = velocityScore;
            this.stockPressureScore = stockPressureScore;
            this.timeDecayScore = timeDecayScore;
        }

        public Long getId() { return beverageId; }
        public void setId(Long id) { this.beverageId = id; }
        public Long getBeverageId() { return beverageId; }
        public void setBeverageId(Long beverageId) { this.beverageId = beverageId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getFlavour() { return flavour; }
        public void setFlavour(String flavour) { this.flavour = flavour; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        public BigDecimal getCurrentCupPrice() { return currentPrice; }
        public void setCurrentCupPrice(BigDecimal currentCupPrice) { this.currentPrice = currentCupPrice; }
        public BigDecimal getPreviousPrice() { return previousPrice; }
        public void setPreviousPrice(BigDecimal previousPrice) { this.previousPrice = previousPrice; }
        public BigDecimal getPriceDelta() { return priceDelta; }
        public void setPriceDelta(BigDecimal priceDelta) { this.priceDelta = priceDelta; }
        public double getPriceChangePct() { return priceChangePct; }
        public void setPriceChangePct(double priceChangePct) { this.priceChangePct = priceChangePct; }
        public String getTrendDirection() { return trendDirection; }
        public void setTrendDirection(String trendDirection) { this.trendDirection = trendDirection; }
        public double getDemandScore() { return demandScore; }
        public void setDemandScore(double demandScore) { this.demandScore = demandScore; }
        public double getVelocityScore() { return velocityScore; }
        public void setVelocityScore(double velocityScore) { this.velocityScore = velocityScore; }
        public double getStockPressureScore() { return stockPressureScore; }
        public void setStockPressureScore(double stockPressureScore) { this.stockPressureScore = stockPressureScore; }
        public double getTimeDecayScore() { return timeDecayScore; }
        public void setTimeDecayScore(double timeDecayScore) { this.timeDecayScore = timeDecayScore; }

        public static ProductPriceDTOBuilder builder() { return new ProductPriceDTOBuilder(); }
        public static class ProductPriceDTOBuilder {
            private Long beverageId;
            private String name;
            private String flavour;
            private BigDecimal currentPrice;
            private BigDecimal previousPrice;
            private BigDecimal priceDelta;
            private double priceChangePct;
            private String trendDirection;
            private double demandScore;
            private double velocityScore;
            private double stockPressureScore;
            private double timeDecayScore;

            public ProductPriceDTOBuilder beverageId(Long beverageId) { this.beverageId = beverageId; return this; }
            public ProductPriceDTOBuilder name(String name) { this.name = name; return this; }
            public ProductPriceDTOBuilder flavour(String flavour) { this.flavour = flavour; return this; }
            public ProductPriceDTOBuilder currentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; return this; }
            public ProductPriceDTOBuilder previousPrice(BigDecimal previousPrice) { this.previousPrice = previousPrice; return this; }
            public ProductPriceDTOBuilder priceDelta(BigDecimal priceDelta) { this.priceDelta = priceDelta; return this; }
            public ProductPriceDTOBuilder priceChangePct(double priceChangePct) { this.priceChangePct = priceChangePct; return this; }
            public ProductPriceDTOBuilder trendDirection(String trendDirection) { this.trendDirection = trendDirection; return this; }
            public ProductPriceDTOBuilder demandScore(double demandScore) { this.demandScore = demandScore; return this; }
            public ProductPriceDTOBuilder velocityScore(double velocityScore) { this.velocityScore = velocityScore; return this; }
            public ProductPriceDTOBuilder stockPressureScore(double stockPressureScore) { this.stockPressureScore = stockPressureScore; return this; }
            public ProductPriceDTOBuilder timeDecayScore(double timeDecayScore) { this.timeDecayScore = timeDecayScore; return this; }
            public ProductPriceDTO build() { return new ProductPriceDTO(beverageId, name, flavour, currentPrice, previousPrice, priceDelta, priceChangePct, trendDirection, demandScore, velocityScore, stockPressureScore, timeDecayScore); }
        }
    }

    /**
     * Dedicated 60-Second Automated Pricing Cycle Execution
     * Evaluates: Demand Velocity, Inventory Pressure, Time Decay, Cross Elasticity, Margin Protection
     */
    @Scheduled(fixedRate = 60000) // Runs every 60 seconds
    @Transactional
    public PriceEvaluationCycleResult execute60SecondPricingEngine() {
        log.info("⚡ Running 60-Second Enterprise Dynamic Pricing Engine Cycle...");

        if (marketCrashService.isCrashActive()) {
            log.warn("🚨 Market Crash Routine is currently ACTIVE. Standard pricing engine paused.");
            return PriceEvaluationCycleResult.builder()
                    .timestamp(LocalDateTime.now().toString())
                    .evaluatedProductsCount(0)
                    .updatedPrices(Collections.emptyList())
                    .marketStatus("MARKET_CRASH_ACTIVE")
                    .build();
        }

        List<Product> products = productRepository.findAll();
        List<ProductPriceDTO> dtoList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Product product : products) {
            BigDecimal oldPrice = product.getCurrentCupPrice();

            // Evaluate & adjust price using domain rules (cooldown, min/max bounds, system config weights)
            PriceAdjustmentService.PriceEvaluationResult evalResult = priceAdjustmentService.evaluateAndAdjustPrice(product.getId());

            Product reloadedProduct = productRepository.findById(product.getId()).orElse(product);
            BigDecimal newPrice = reloadedProduct.getCurrentCupPrice();
            BigDecimal priceDelta = newPrice.subtract(oldPrice);
            double changePct = (oldPrice.doubleValue() > 0) ? (priceDelta.doubleValue() / oldPrice.doubleValue()) * 100.0 : 0.0;
            String trendDirection = priceDelta.compareTo(BigDecimal.ZERO) > 0 ? "UP" : (priceDelta.compareTo(BigDecimal.ZERO) < 0 ? "DOWN" : "FLAT");

            double velScore = demandCalculationService.calculateVelocityScore(reloadedProduct.getId(), 15);
            double stockPct = stockPressureService.calculateStockPressurePercentage(reloadedProduct.getId());
            double timeMult = timeFactorService.getTimeFactorMultiplier(now.toLocalTime());

            ProductPriceDTO dto = ProductPriceDTO.builder()
                    .beverageId(reloadedProduct.getId())
                    .name(reloadedProduct.getName())
                    .flavour(reloadedProduct.getFlavour())
                    .currentPrice(newPrice)
                    .previousPrice(oldPrice)
                    .priceDelta(priceDelta)
                    .priceChangePct(BigDecimal.valueOf(changePct).setScale(1, RoundingMode.HALF_UP).doubleValue())
                    .trendDirection(trendDirection)
                    .demandScore(evalResult.getDemandScore())
                    .velocityScore(velScore)
                    .stockPressureScore(stockPct)
                    .timeDecayScore(timeMult)
                    .build();

            dtoList.add(dto);

            // Update Redis Cache for fast REST retrieval
            try {
                if (redisTemplate != null) {
                    redisTemplate.opsForValue().set("live_price:" + reloadedProduct.getId(), dto);
                }
            } catch (Exception e) {
                log.debug("Redis cache write bypassed: {}", e.getMessage());
            }
        }

        PriceEvaluationCycleResult cycleResult = PriceEvaluationCycleResult.builder()
                .timestamp(now.toString())
                .evaluatedProductsCount(dtoList.size())
                .updatedPrices(dtoList)
                .marketStatus("TRADING_NORMAL")
                .build();

        // Broadcast live prices over STOMP WebSocket topic
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/prices", cycleResult);
                messagingTemplate.convertAndSend("/topic/led-display", cycleResult);
            }
        } catch (Exception e) {
            log.debug("WebSocket broadcast bypass: {}", e.getMessage());
        }

        return cycleResult;
    }
}
