package com.retailpos.pricing;

import com.retailpos.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MarketCrashService {

    private static final Logger log = LoggerFactory.getLogger(MarketCrashService.class);

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private boolean crashActive = false;
    private LocalDateTime crashEndTime;
    private String currentCrashCode;
    private String triggerSource = "MANUAL_ADMIN";

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
        private LocalDateTime endTime;
        private String message;

        public MarketCrashStatus() {}
        public MarketCrashStatus(boolean active, String eventCode, String triggerType, long remainingSeconds, LocalDateTime endTime, String message) {
            this.active = active;
            this.eventCode = eventCode;
            this.triggerType = triggerType;
            this.remainingSeconds = remainingSeconds;
            this.endTime = endTime;
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
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public static MarketCrashStatusBuilder builder() { return new MarketCrashStatusBuilder(); }
        public static class MarketCrashStatusBuilder {
            private boolean active;
            private String eventCode;
            private String triggerType;
            private long remainingSeconds;
            private LocalDateTime endTime;
            private String message;

            public MarketCrashStatusBuilder active(boolean active) { this.active = active; return this; }
            public MarketCrashStatusBuilder eventCode(String eventCode) { this.eventCode = eventCode; return this; }
            public MarketCrashStatusBuilder triggerType(String triggerType) { this.triggerType = triggerType; return this; }
            public MarketCrashStatusBuilder remainingSeconds(long remainingSeconds) { this.remainingSeconds = remainingSeconds; return this; }
            public MarketCrashStatusBuilder endTime(LocalDateTime endTime) { this.endTime = endTime; return this; }
            public MarketCrashStatusBuilder message(String message) { this.message = message; return this; }
            public MarketCrashStatus build() { return new MarketCrashStatus(active, eventCode, triggerType, remainingSeconds, endTime, message); }
        }
    }

    public synchronized MarketCrashStatus getStatus() {
        if (crashActive && LocalDateTime.now().isAfter(crashEndTime)) {
            crashActive = false;
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
                .endTime(crashEndTime)
                .message(crashActive ? "🚨 MARKET CRASH IN PROGRESS! All prices set to absolute floor!" : "Trading normal. Dynamic price algorithm active.")
                .build();
    }

    /**
     * Random Algorithm Market Crash Trigger (Runs periodic probability check every 15 minutes)
     */
    @Scheduled(fixedRate = 900000)
    public void checkForRandomAlgorithmCrash() {
        if (crashActive) return;
        // 10% chance of random crash in high trading hours
        if (new Random().nextDouble() < 0.10) {
            log.info("🎲 Random Algorithmic Market Crash Event Triggered!");
            triggerMarketCrash(3, "RANDOM_ALGORITHM");
        }
    }

    @Transactional
    public synchronized MarketCrashStatus triggerMarketCrash(int durationMinutes, String triggerType) {
        int duration = (durationMinutes > 0) ? durationMinutes : 3;
        this.crashActive = true;
        this.crashEndTime = LocalDateTime.now().plusMinutes(duration);
        this.currentCrashCode = "CRASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.triggerSource = (triggerType != null) ? triggerType : "MANUAL_ADMIN";

        List<Product> products = productRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (Product product : products) {
            BigDecimal oldPrice = product.getCurrentCupPrice();
            BigDecimal floorPrice = product.getMinCupPrice();

            if (oldPrice == null || oldPrice.compareTo(floorPrice) != 0) {
                product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
            }
            product.setCurrentCupPrice(floorPrice);
            product.setLastPriceChangeTimestamp(now);
            productRepository.save(product);

            PriceHistory history = PriceHistory.builder()
                    .productId(product.getId())
                    .oldPrice(oldPrice)
                    .newPrice(floorPrice)
                    .demandScore(0.0)
                    .stockPressurePct(0.0)
                    .timeFactorMultiplier(1.0)
                    .explanation(String.format("🚨 MARKET CRASH (%s)! Price dropped from ₹%s to floor limit ₹%s.", triggerSource, oldPrice, floorPrice))
                    .createdAt(now)
                    .build();
            priceHistoryRepository.save(history);
        }

        MarketCrashStatus status = getStatus();

        // STOMP WebSocket broadcast
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
        this.crashActive = false;
        this.crashEndTime = LocalDateTime.now();
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
            crashActive = false;
        }
        return crashActive;
    }
}
