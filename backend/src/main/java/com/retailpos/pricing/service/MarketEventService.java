package com.retailpos.pricing.service;

import com.retailpos.domain.*;
import com.retailpos.pricing.MarketCrashService;
import com.retailpos.pricing.PricingEngineService.ProductPriceDTO;
import com.retailpos.pricing.model.PurchaseEvent;
import com.retailpos.pricing.redis.PricingRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MarketEventService {

    private static final Logger log = LoggerFactory.getLogger(MarketEventService.class);

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketEventRepository marketEventRepository;
    private final MarketCorrelationService correlationService;
    private final PricingRedisRepository redisRepository;
    private final PriceBroadcastService broadcastService;
    private final MarketCrashService marketCrashService;

    @Value("${market.crash.volume-threshold:500}")
    private long globalCrashVolumeThreshold;

    public MarketEventService(ProductRepository productRepository,
                               PriceHistoryRepository priceHistoryRepository,
                               MarketEventRepository marketEventRepository,
                               MarketCorrelationService correlationService,
                               PricingRedisRepository redisRepository,
                               PriceBroadcastService broadcastService,
                               MarketCrashService marketCrashService) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.marketEventRepository = marketEventRepository;
        this.correlationService = correlationService;
        this.redisRepository = redisRepository;
        this.broadcastService = broadcastService;
        this.marketCrashService = marketCrashService;
    }

    @Transactional
    public List<ProductPriceDTO> handlePurchaseEvent(PurchaseEvent event) {
        if (event == null || event.getProductId() == null) {
            return Collections.emptyList();
        }

        Long purchasedId = event.getProductId();
        int qty = event.getQuantity() > 0 ? event.getQuantity() : 1;
        LocalDateTime now = LocalDateTime.now();

        log.info("⚡ [MARKET EVENT] Purchase Triggered: ProductId={} OrderId={} Qty={} ExecutedPrice=₹{}",
                purchasedId, event.getOrderId(), qty, event.getExecutedPrice());

        // Increment global transaction volume in Redis
        long totalVolume = redisRepository.incrementMarketVolume(qty);

        // Check global volume crash threshold trigger
        if (totalVolume >= globalCrashVolumeThreshold && !marketCrashService.isCrashActive()) {
            log.warn("🚨 Global Volume Threshold Reached ({} >= {})! Triggering Market Crash!", totalVolume, globalCrashVolumeThreshold);
            marketCrashService.triggerMarketCrash();
            return Collections.emptyList();
        }

        // Fetch purchased product
        Product mainProduct = productRepository.findById(purchasedId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + purchasedId));

        BigDecimal oldMainPrice = mainProduct.getCurrentCupPrice() != null ? mainProduct.getCurrentCupPrice() : mainProduct.getDefaultCupPrice();
        BigDecimal minPrice = mainProduct.getMinCupPrice() != null ? mainProduct.getMinCupPrice() : new BigDecimal("18.00");
        BigDecimal maxPrice = mainProduct.getMaxCupPrice() != null ? mainProduct.getMaxCupPrice() : new BigDecimal("35.00");

        // Direct demand impact shift calculation: ONE SUCCESSFUL ORDER = ONE MARKET EVENT (+₹1.00)
        int directShift = 1;
        BigDecimal directDelta = BigDecimal.valueOf(directShift);
        BigDecimal newMainPrice = oldMainPrice.add(directDelta).max(minPrice).min(maxPrice).setScale(2, RoundingMode.HALF_UP);

        // Update purchased product in DB & Redis
        mainProduct.setCurrentCupPrice(newMainPrice);
        mainProduct.setPriceVersion((mainProduct.getPriceVersion() != null ? mainProduct.getPriceVersion() : 1) + 1);
        mainProduct.setLastPriceChangeTimestamp(now);
        mainProduct.setOrderCount(0); // reset count after applying surge
        productRepository.saveAndFlush(mainProduct);

        redisRepository.setProductPrice(mainProduct.getId(), newMainPrice);
        redisRepository.setLastTradeTimestamp(mainProduct.getId(), now);

        int newMarketVersion = redisRepository.incrementMarketVersion();

        // Audit main product price change
        PriceHistory mainHistory = PriceHistory.builder()
                .productId(mainProduct.getId())
                .oldPrice(oldMainPrice)
                .newPrice(newMainPrice)
                .priceChange(newMainPrice.subtract(oldMainPrice))
                .reason("PURCHASE_DEMAND_SURGE")
                .explanation("Direct purchase surge for " + mainProduct.getName())
                .calculationWindowStart(now.minusSeconds(60))
                .calculationWindowEnd(now)
                .rawW0(qty)
                .rawW1(0)
                .rawW2(0)
                .unconsumedW0(0)
                .weightedSales((double) qty)
                .targetSales(mainProduct.getTargetSalesPer1Minute() != null ? mainProduct.getTargetSalesPer1Minute() : 0.55)
                .demandRatio((double) qty / (mainProduct.getTargetSalesPer1Minute() != null ? mainProduct.getTargetSalesPer1Minute() : 0.55))
                .priceVersion(mainProduct.getPriceVersion())
                .createdAt(now)
                .build();
        priceHistoryRepository.save(mainHistory);

        // Log market event for main purchase
        MarketEvent mEvent = MarketEvent.builder()
                .eventType("PURCHASE_SURGE")
                .productId(mainProduct.getId())
                .quantity(qty)
                .priceBefore(oldMainPrice)
                .priceAfter(newMainPrice)
                .marketVersion(newMarketVersion)
                .details(String.format("Direct purchase surge: %d cups of %s (₹%s -> ₹%s)", qty, mainProduct.getName(), oldMainPrice, newMainPrice))
                .build();
        marketEventRepository.save(mEvent);

        List<ProductPriceDTO> changedDTOs = new ArrayList<>();

        BigDecimal baseP = mainProduct.getDefaultCupPrice() != null ? mainProduct.getDefaultCupPrice() : new BigDecimal("25.00");
        double mainChangePct = ((newMainPrice.subtract(baseP)).doubleValue() / baseP.doubleValue()) * 100.0;

        changedDTOs.add(ProductPriceDTO.builder()
                .beverageId(mainProduct.getId())
                .name(mainProduct.getName())
                .flavour(mainProduct.getFlavour())
                .currentPrice(newMainPrice)
                .effectivePrice(newMainPrice)
                .previousPrice(oldMainPrice)
                .priceDelta(newMainPrice.subtract(oldMainPrice))
                .priceVersion(mainProduct.getPriceVersion())
                .priceChangePct(BigDecimal.valueOf(mainChangePct).setScale(1, RoundingMode.HALF_UP).doubleValue())
                .trendDirection("UP")
                .demandRatio((double) qty)
                .demandLevelCategory("HIGH")
                .minCupPrice(mainProduct.getMinCupPrice())
                .maxCupPrice(mainProduct.getMaxCupPrice())
                .build());

        // Secondary correlation recalculation for related products
        List<ProductCorrelation> correlations = correlationService.getCorrelationsForSourceProduct(purchasedId);
        for (ProductCorrelation corr : correlations) {
            if (!corr.getEnabled()) continue;

            Product targetProduct = corr.getTargetProduct();
            if (targetProduct == null || !targetProduct.getIsActive()) continue;

            BigDecimal coeff = corr.getCorrelationCoefficient();
            BigDecimal secondaryDelta = correlationService.calculateSecondaryImpact(directDelta, coeff);

            if (secondaryDelta.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal targetOldPrice = targetProduct.getCurrentCupPrice() != null ? targetProduct.getCurrentCupPrice() : targetProduct.getDefaultCupPrice();
                BigDecimal targetMin = targetProduct.getMinCupPrice() != null ? targetProduct.getMinCupPrice() : new BigDecimal("18.00");
                BigDecimal targetMax = targetProduct.getMaxCupPrice() != null ? targetProduct.getMaxCupPrice() : new BigDecimal("35.00");

                BigDecimal targetNewPrice = targetOldPrice.add(secondaryDelta).max(targetMin).min(targetMax).setScale(2, RoundingMode.HALF_UP);

                if (targetNewPrice.compareTo(targetOldPrice) != 0) {
                    targetProduct.setCurrentCupPrice(targetNewPrice);
                    targetProduct.setPriceVersion((targetProduct.getPriceVersion() != null ? targetProduct.getPriceVersion() : 1) + 1);
                    targetProduct.setLastPriceChangeTimestamp(now);
                    productRepository.saveAndFlush(targetProduct);

                    redisRepository.setProductPrice(targetProduct.getId(), targetNewPrice);

                    PriceHistory targetHist = PriceHistory.builder()
                            .productId(targetProduct.getId())
                            .oldPrice(targetOldPrice)
                            .newPrice(targetNewPrice)
                            .priceChange(targetNewPrice.subtract(targetOldPrice))
                            .reason("CORRELATED_MARKET_IMPACT")
                            .explanation(String.format("Correlated surge from %s (coeff %s)", mainProduct.getName(), coeff))
                            .calculationWindowStart(now.minusSeconds(120))
                            .calculationWindowEnd(now)
                            .priceVersion(targetProduct.getPriceVersion())
                            .createdAt(now)
                            .build();
                    priceHistoryRepository.save(targetHist);

                    MarketEvent corrEvent = MarketEvent.builder()
                            .eventType("CORRELATED_MOVEMENT")
                            .productId(targetProduct.getId())
                            .quantity(1)
                            .priceBefore(targetOldPrice)
                            .priceAfter(targetNewPrice)
                            .marketVersion(newMarketVersion)
                            .details(String.format("Correlated surge from %s (coeff %s): ₹%s -> ₹%s", mainProduct.getName(), coeff, targetOldPrice, targetNewPrice))
                            .build();
                    marketEventRepository.save(corrEvent);

                    BigDecimal tBase = targetProduct.getDefaultCupPrice() != null ? targetProduct.getDefaultCupPrice() : new BigDecimal("25.00");
                    double targetPct = ((targetNewPrice.subtract(tBase)).doubleValue() / tBase.doubleValue()) * 100.0;

                    changedDTOs.add(ProductPriceDTO.builder()
                            .beverageId(targetProduct.getId())
                            .name(targetProduct.getName())
                            .flavour(targetProduct.getFlavour())
                            .currentPrice(targetNewPrice)
                            .effectivePrice(targetNewPrice)
                            .previousPrice(targetOldPrice)
                            .priceDelta(targetNewPrice.subtract(targetOldPrice))
                            .priceVersion(targetProduct.getPriceVersion())
                            .priceChangePct(BigDecimal.valueOf(targetPct).setScale(1, RoundingMode.HALF_UP).doubleValue())
                            .trendDirection("UP")
                            .demandRatio(coeff.doubleValue())
                            .demandLevelCategory("CORRELATED_SURGE")
                            .minCupPrice(targetProduct.getMinCupPrice())
                            .maxCupPrice(targetProduct.getMaxCupPrice())
                            .build());

                    log.info("🔗 [CORRELATED SURGE] Target ProductId={} ({}) reacted to purchase of {}: ₹{} -> ₹{} (coeff={})",
                            targetProduct.getId(), targetProduct.getName(), mainProduct.getName(), targetOldPrice, targetNewPrice, coeff);
                }
            }
        }

        // Broadcast updated price changes via WebSocket
        broadcastService.broadcastPriceUpdate(newMarketVersion, changedDTOs);

        return changedDTOs;
    }
}
