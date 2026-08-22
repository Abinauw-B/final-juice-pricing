package com.retailpos.pricing;

import com.retailpos.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MarketCrashService {

    private static final Logger log = LoggerFactory.getLogger(MarketCrashService.class);

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private boolean crashActive = false;
    private LocalDateTime crashStartedTime;
    private LocalDateTime crashEndTime;
    private String currentCrashCode;
    private String triggerSource = "MANUAL_ADMIN";

    private final Set<Long> crashedProductIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private BigDecimal crashPrice = new BigDecimal("18.00");

    public MarketCrashService(ProductRepository productRepository, PriceHistoryRepository priceHistoryRepository, SimpMessagingTemplate messagingTemplate) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.messagingTemplate = messagingTemplate;
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

        return MarketCrashStatus.builder()
                .active(crashActive)
                .eventCode(currentCrashCode)
                .triggerType(triggerSource)
                .remainingSeconds(remaining)
                .startTime(crashStartedTime)
                .endTime(crashEndTime)
                .affectedProductIds(new ArrayList<>(crashedProductIds))
                .crashPrice(crashPrice)
                .message(crashActive ? "🚨 MARKET CRASH IN PROGRESS! Selected juices temporarily set to ₹" + crashPrice + "!" : "Trading normal. Dynamic price algorithm active.")
                .build();
    }

    public boolean isProductCrashed(Long productId) {
        if (!isCrashActive()) {
            return false;
        }
        return crashedProductIds.contains(productId);
    }

    public BigDecimal getEffectivePrice(Product product) {
        if (product == null) return BigDecimal.ZERO;
        if (isProductCrashed(product.getId())) {
            return crashPrice;
        }
        return product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : product.getDefaultCupPrice();
    }

    /**
     * Periodic random check for algorithmic market crash (Runs every 20-30 minutes)
     */
    @Scheduled(fixedRate = 1200000) // Every 20 minutes
    public void checkForRandomAlgorithmCrash() {
        if (crashActive) return;
        // 15% probability during scheduled window
        if (new Random().nextDouble() < 0.15) {
            log.info("🎲 Random Algorithmic Market Crash Event Triggered!");
            triggerMarketCrash(3, 2, new BigDecimal("18.00"), "RANDOM_ALGORITHM");
        }
    }

    @Transactional
    public synchronized MarketCrashStatus triggerMarketCrash(int durationMinutes, String triggerType) {
        return triggerMarketCrash(durationMinutes, 2, new BigDecimal("18.00"), triggerType);
    }

    @Transactional
    public synchronized MarketCrashStatus triggerMarketCrash(int durationMinutes, int juiceCount, BigDecimal crashPriceVal, String triggerType) {
        int duration = (durationMinutes > 0) ? durationMinutes : 3;
        int targetCount = (juiceCount > 0) ? juiceCount : 2;
        this.crashPrice = (crashPriceVal != null) ? crashPriceVal : new BigDecimal("18.00");
        this.crashActive = true;
        this.crashStartedTime = LocalDateTime.now();
        this.crashEndTime = crashStartedTime.plusMinutes(duration);
        this.currentCrashCode = "CRASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.triggerSource = (triggerType != null) ? triggerType : "MANUAL_ADMIN";

        List<Product> allProducts = productRepository.findAll();
        crashedProductIds.clear();

        if (!allProducts.isEmpty()) {
            // Prefer juices currently priced at ₹25 or above (Section 18)
            List<Product> highPricedJuices = allProducts.stream()
                    .filter(p -> p.getCurrentCupPrice() != null && p.getCurrentCupPrice().compareTo(new BigDecimal("25.00")) >= 0)
                    .collect(Collectors.toList());

            List<Product> selected = new ArrayList<>();
            Collections.shuffle(highPricedJuices);

            for (Product p : highPricedJuices) {
                if (selected.size() < targetCount) {
                    selected.add(p);
                }
            }

            if (selected.size() < targetCount) {
                List<Product> remaining = new ArrayList<>(allProducts);
                remaining.removeAll(selected);
                Collections.shuffle(remaining);
                for (Product p : remaining) {
                    if (selected.size() < targetCount) {
                        selected.add(p);
                    }
                }
            }

            // If juiceCount was set to all or fallback
            if (selected.isEmpty()) {
                selected.addAll(allProducts);
            }

            for (Product p : selected) {
                crashedProductIds.add(p.getId());

                PriceHistory history = PriceHistory.builder()
                        .productId(p.getId())
                        .oldPrice(p.getCurrentCupPrice())
                        .newPrice(crashPrice)
                        .priceChange(crashPrice.subtract(p.getCurrentCupPrice()))
                        .reason("MARKET_CRASH_START")
                        .explanation(String.format("🚨 MARKET CRASH (%s)! Temporary promotional crash price set to ₹%s (Normal price: ₹%s held)", triggerSource, crashPrice, p.getCurrentCupPrice()))
                        .createdAt(crashStartedTime)
                        .build();
                priceHistoryRepository.save(history);
            }
        }

        MarketCrashStatus status = getStatus();

        try {
            messagingTemplate.convertAndSend("/topic/market-crash", status);
            messagingTemplate.convertAndSend("/topic/prices", productRepository.findAll());
        } catch (Exception e) {
            log.debug("WebSocket broadcast bypass: {}", e.getMessage());
        }

        return status;
    }

    @Transactional
    public synchronized MarketCrashStatus stopMarketCrash() {
        if (!crashActive) {
            return getStatus();
        }

        this.crashActive = false;
        LocalDateTime now = LocalDateTime.now();
        this.crashEndTime = now;

        List<Product> allProducts = productRepository.findAll();
        for (Product p : allProducts) {
            if (crashedProductIds.contains(p.getId())) {
                PriceHistory history = PriceHistory.builder()
                        .productId(p.getId())
                        .oldPrice(crashPrice)
                        .newPrice(p.getCurrentCupPrice())
                        .priceChange(p.getCurrentCupPrice().subtract(crashPrice))
                        .reason("MARKET_CRASH_END")
                        .explanation(String.format("🟢 Market Crash ended. Normal calculated price of ₹%s restored.", p.getCurrentCupPrice()))
                        .createdAt(now)
                        .build();
                priceHistoryRepository.save(history);
            }
        }

        crashedProductIds.clear();
        MarketCrashStatus status = getStatus();

        try {
            messagingTemplate.convertAndSend("/topic/market-crash", status);
            messagingTemplate.convertAndSend("/topic/prices", productRepository.findAll());
        } catch (Exception e) {
            log.debug("WebSocket broadcast bypass: {}", e.getMessage());
        }

        return status;
    }

    public boolean isCrashActive() {
        if (crashActive && LocalDateTime.now().isAfter(crashEndTime)) {
            stopMarketCrash();
        }
        return crashActive;
    }
}
