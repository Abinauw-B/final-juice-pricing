package com.retailpos.pricing;

import com.retailpos.domain.*;
import com.retailpos.pricing.redis.PricingRedisRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MarketCrashService {

    private static final Logger log = LoggerFactory.getLogger(MarketCrashService.class);

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketCrashSnapshotRepository snapshotRepository;
    private final PricingRedisRepository redisRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private boolean crashActive = false;
    private LocalDateTime crashStartedTime;
    private LocalDateTime crashEndTime;
    private String currentCrashCode;
    private String triggerSource = "MANUAL_ADMIN";

    private final Set<Long> crashedProductIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final PricingConfigurationService pricingConfigurationService;

    public MarketCrashService(ProductRepository productRepository, 
                              PriceHistoryRepository priceHistoryRepository,
                              MarketCrashSnapshotRepository snapshotRepository,
                              PricingRedisRepository redisRepository,
                              SimpMessagingTemplate messagingTemplate,
                              PricingConfigurationService pricingConfigurationService) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.snapshotRepository = snapshotRepository;
        this.redisRepository = redisRepository;
        this.messagingTemplate = messagingTemplate;
        this.pricingConfigurationService = pricingConfigurationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initCrashStateFromStorage() {
        try {
            boolean activeInRedis = redisRepository.isCrashActiveInRedis();
            String codeInRedis = redisRepository.getCrashCodeFromRedis();
            String endTimeStr = redisRepository.getCrashEndTimeFromRedis();

            if (activeInRedis && codeInRedis != null && endTimeStr != null) {
                LocalDateTime end = LocalDateTime.parse(endTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                LocalDateTime now = LocalDateTime.now();

                if (now.isBefore(end)) {
                    log.info("🚨 [CRASH RECOVERY] Active market crash detected from storage (code={}, endsAt={}). Resuming crash timer.", codeInRedis, end);
                    this.crashActive = true;
                    this.currentCrashCode = codeInRedis;
                    this.crashEndTime = end;
                    this.crashStartedTime = end.minusSeconds(180);
                    this.triggerSource = "RECOVERY_RESUME";
                    
                    List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
                    for (Product p : products) {
                        crashedProductIds.add(p.getId());
                    }
                } else {
                    log.info("🚨 [CRASH RECOVERY] Expired market crash detected on startup. Triggering automatic price restoration.");
                    this.crashActive = true;
                    this.currentCrashCode = codeInRedis;
                    this.crashEndTime = end;
                    stopMarketCrash();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to initialize crash state on startup: {}", e.getMessage());
        }
    }

    public static class MarketCrashStatus {
        private boolean active;
        private String eventCode;
        private String triggerType;
        private long remainingSeconds;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private List<Long> affectedProductIds;
        private BigDecimal crashPrice;
        private String message;

        public MarketCrashStatus() {}
        public MarketCrashStatus(boolean active, String eventCode, String triggerType, long remainingSeconds, LocalDateTime startTime, LocalDateTime endTime, List<Long> affectedProductIds, BigDecimal crashPrice, String message) {
            this.active = active;
            this.eventCode = eventCode;
            this.triggerType = triggerType;
            this.remainingSeconds = remainingSeconds;
            this.startTime = startTime;
            this.endTime = endTime;
            this.affectedProductIds = affectedProductIds;
            this.crashPrice = crashPrice;
            this.message = message;
        }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public String getEventCode() { return eventCode; }
        public void setEventCode(String eventCode) { this.eventCode = eventCode; }
        public String getTriggerType() { return triggerType; }
        public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
        public long getRemainingSeconds() { return remainingSeconds; }
        public void setRemainingSeconds(long remainingSeconds) { this.remainingSeconds = remainingSeconds; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public List<Long> getAffectedProductIds() { return affectedProductIds; }
        public void setAffectedProductIds(List<Long> affectedProductIds) { this.affectedProductIds = affectedProductIds; }
        public BigDecimal getCrashPrice() { return crashPrice; }
        public void setCrashPrice(BigDecimal crashPrice) { this.crashPrice = crashPrice; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public static MarketCrashStatusBuilder builder() { return new MarketCrashStatusBuilder(); }
        public static class MarketCrashStatusBuilder {
            private boolean active;
            private String eventCode;
            private String triggerType;
            private long remainingSeconds;
            private LocalDateTime startTime;
            private LocalDateTime endTime;
            private List<Long> affectedProductIds;
            private BigDecimal crashPrice;
            private String message;

            public MarketCrashStatusBuilder active(boolean active) { this.active = active; return this; }
            public MarketCrashStatusBuilder eventCode(String eventCode) { this.eventCode = eventCode; return this; }
            public MarketCrashStatusBuilder triggerType(String triggerType) { this.triggerType = triggerType; return this; }
            public MarketCrashStatusBuilder remainingSeconds(long remainingSeconds) { this.remainingSeconds = remainingSeconds; return this; }
            public MarketCrashStatusBuilder startTime(LocalDateTime startTime) { this.startTime = startTime; return this; }
            public MarketCrashStatusBuilder endTime(LocalDateTime endTime) { this.endTime = endTime; return this; }
            public MarketCrashStatusBuilder affectedProductIds(List<Long> affectedProductIds) { this.affectedProductIds = affectedProductIds; return this; }
            public MarketCrashStatusBuilder crashPrice(BigDecimal crashPrice) { this.crashPrice = crashPrice; return this; }
            public MarketCrashStatusBuilder message(String message) { this.message = message; return this; }
            public MarketCrashStatus build() { return new MarketCrashStatus(active, eventCode, triggerType, remainingSeconds, startTime, endTime, affectedProductIds, crashPrice, message); }
        }
    }

    public synchronized MarketCrashStatus getStatus() {
        if (crashActive && LocalDateTime.now().isAfter(crashEndTime)) {
            stopMarketCrash();
        }

        long remaining = 0;
        if (crashActive && crashEndTime != null) {
            remaining = Math.max(0, crashEndTime.toEpochSecond(ZoneOffset.UTC) - LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        }

        BigDecimal displayCrashPrice = pricingConfigurationService != null ? pricingConfigurationService.getMarketCrashPrice() : new BigDecimal("18.00");

        return MarketCrashStatus.builder()
                .active(crashActive)
                .eventCode(currentCrashCode)
                .triggerType(triggerSource)
                .remainingSeconds(remaining)
                .startTime(crashStartedTime)
                .endTime(crashEndTime)
                .affectedProductIds(new ArrayList<>(crashedProductIds))
                .crashPrice(displayCrashPrice)
                .message(crashActive ? "🚨 MARKET CRASH IN PROGRESS! All juices set to ₹" + displayCrashPrice + " crash price!" : "Trading normal. Dynamic price algorithm active.")
                .build();
    }

    public boolean isProductCrashed(Long productId) {
        if (!isCrashActive()) {
            return false;
        }
        return crashedProductIds.contains(productId);
    }

    public BigDecimal calculateCrashPrice(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null for crash calculation");
        }
        if (product.getMinCupPrice() == null) {
            throw new IllegalArgumentException("Product floor price (minCupPrice) is required for product ID: " + product.getId());
        }
        if (product.getMaxCupPrice() == null) {
            throw new IllegalArgumentException("Product ceiling price (maxCupPrice) is required for product ID: " + product.getId());
        }
        BigDecimal floor = product.getMinCupPrice();
        return floor.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getEffectivePrice(Product product) {
        if (product == null) return BigDecimal.ZERO;
        if (isProductCrashed(product.getId())) {
            return calculateCrashPrice(product);
        }
        return product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : product.getDefaultCupPrice();
    }

    @Transactional
    public synchronized MarketCrashStatus triggerMarketCrash() {
        int durationSec = pricingConfigurationService != null ? pricingConfigurationService.getMarketCrashDurationSeconds() : 180;
        return triggerMarketCrash(Math.max(1, durationSec / 60), "GLOBAL_VOLUME_TRIGGER");
    }

    @Transactional
    public synchronized MarketCrashStatus triggerMarketCrash(int durationMinutes, String triggerType) {
        int configuredSec = pricingConfigurationService != null ? pricingConfigurationService.getMarketCrashDurationSeconds() : 180;
        int durationSeconds = (durationMinutes > 0) ? durationMinutes * 60 : configuredSec;
        this.crashActive = true;
        this.crashStartedTime = LocalDateTime.now();
        this.crashEndTime = crashStartedTime.plusSeconds(durationSeconds);
        this.currentCrashCode = "CRASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.triggerSource = (triggerType != null) ? triggerType : "MANUAL_ADMIN";

        BigDecimal configuredCrashPrice = pricingConfigurationService != null ? pricingConfigurationService.getMarketCrashPrice() : new BigDecimal("18.00");

        List<Product> allProducts = productRepository.findByIsActiveTrueOrderByIdAsc();
        crashedProductIds.clear();

        // 1. Persist crash metadata in Redis
        redisRepository.setCrashState(true, currentCrashCode, crashEndTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        for (Product p : allProducts) {
            crashedProductIds.add(p.getId());
            BigDecimal preCrashPrice = p.getCurrentCupPrice() != null ? p.getCurrentCupPrice() : p.getDefaultCupPrice();
            BigDecimal crashPrice = configuredCrashPrice;

            // 2. Save immutable pre-crash snapshot in DB & Redis
            MarketCrashSnapshot snapshot = MarketCrashSnapshot.builder()
                    .crashCode(currentCrashCode)
                    .productId(p.getId())
                    .preCrashPrice(preCrashPrice)
                    .createdAt(crashStartedTime)
                    .build();
            snapshotRepository.save(snapshot);
            redisRepository.setCrashSnapshot(p.getId(), preCrashPrice);

            // 3. Set live price to crash price
            p.setCurrentCupPrice(crashPrice);
            p.setPriceVersion((p.getPriceVersion() != null ? p.getPriceVersion() : 1) + 1);
            p.setLastPriceChangeTimestamp(crashStartedTime);
            productRepository.save(p);
            redisRepository.setProductPrice(p.getId(), crashPrice);

            PriceHistory history = PriceHistory.builder()
                    .productId(p.getId())
                    .oldPrice(preCrashPrice)
                    .newPrice(crashPrice)
                    .priceChange(crashPrice.subtract(preCrashPrice))
                    .reason("MARKET_CRASH_START")
                    .explanation(String.format("🚨 MARKET CRASH STARTED! Price snapshot of ₹%s saved; live price set to ₹%s", preCrashPrice, crashPrice))
                    .configVersion(pricingConfigurationService != null ? pricingConfigurationService.getConfigurationVersion() : 1L)
                    .createdAt(crashStartedTime)
                    .build();
            priceHistoryRepository.save(history);
        }

        MarketCrashStatus status = getStatus();

        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/market-crash", status);
                messagingTemplate.convertAndSend("/topic/prices", productRepository.findByIsActiveTrueOrderByIdAsc());
            }
        } catch (Exception e) {
            log.debug("WebSocket broadcast bypass: {}", e.getMessage());
        }

        return status;
    }

    @Transactional
    public synchronized MarketCrashStatus triggerMarketCrash(int durationMinutes, int juiceCount, BigDecimal crashPriceVal, String triggerType) {
        return triggerMarketCrash(durationMinutes, triggerType);
    }

    @Transactional
    public synchronized MarketCrashStatus stopMarketCrash() {
        if (!crashActive && currentCrashCode == null) {
            return getStatus();
        }

        this.crashActive = false;
        LocalDateTime now = LocalDateTime.now();
        this.crashEndTime = now;

        List<Product> allProducts = productRepository.findByIsActiveTrueOrderByIdAsc();
        List<MarketCrashSnapshot> snapshots = (currentCrashCode != null) ? snapshotRepository.findByCrashCode(currentCrashCode) : Collections.emptyList();
        Map<Long, BigDecimal> snapshotMap = new HashMap<>();

        for (MarketCrashSnapshot s : snapshots) {
            snapshotMap.put(s.getProductId(), s.getPreCrashPrice());
        }

        for (Product p : allProducts) {
            BigDecimal restoredPrice = snapshotMap.get(p.getId());
            if (restoredPrice == null) {
                restoredPrice = redisRepository.getCrashSnapshot(p.getId());
            }
            if (restoredPrice == null) {
                restoredPrice = p.getDefaultCupPrice() != null ? p.getDefaultCupPrice() : new BigDecimal("25.00");
            }

            BigDecimal currentPrice = p.getCurrentCupPrice() != null ? p.getCurrentCupPrice() : new BigDecimal("18.00");
            p.setCurrentCupPrice(restoredPrice);
            p.setPriceVersion((p.getPriceVersion() != null ? p.getPriceVersion() : 1) + 1);
            p.setLastPriceChangeTimestamp(now);
            productRepository.save(p);
            redisRepository.setProductPrice(p.getId(), restoredPrice);

            PriceHistory history = PriceHistory.builder()
                    .productId(p.getId())
                    .oldPrice(currentPrice)
                    .newPrice(restoredPrice)
                    .priceChange(restoredPrice.subtract(currentPrice))
                    .reason("MARKET_CRASH_ENDED")
                    .explanation(String.format("🟢 Market Crash ended. Pre-crash snapshot price of ₹%s restored.", restoredPrice))
                    .createdAt(now)
                    .build();
            priceHistoryRepository.save(history);
        }

        crashedProductIds.clear();
        redisRepository.clearCrashStateInRedis();
        currentCrashCode = null;

        MarketCrashStatus status = getStatus();

        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/market-crash", status);
                messagingTemplate.convertAndSend("/topic/prices", productRepository.findByIsActiveTrueOrderByIdAsc());
            }
        } catch (Exception e) {
            log.debug("WebSocket broadcast bypass: {}", e.getMessage());
        }

        return status;
    }

    public boolean isCrashActive() {
        if (crashActive && crashEndTime != null && LocalDateTime.now().isAfter(crashEndTime)) {
            stopMarketCrash();
        }
        return crashActive;
    }
}

