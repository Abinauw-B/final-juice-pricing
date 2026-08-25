package com.retailpos.pricing.service;

import com.retailpos.domain.PriceHistory;
import com.retailpos.domain.PriceHistoryRepository;
import com.retailpos.domain.Product;
import com.retailpos.domain.ProductRepository;
import com.retailpos.pricing.PricingEngineService.ProductPriceDTO;
import com.retailpos.pricing.redis.PricingRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PriceDecayService {

    private static final Logger log = LoggerFactory.getLogger(PriceDecayService.class);

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PricingRedisRepository redisRepository;
    private final PriceBroadcastService broadcastService;

    @Value("${pricing.decay.interval-seconds:300}")
    private long decayIntervalSeconds;

    @Value("${pricing.decay.step:1.00}")
    private BigDecimal decayStep;

    public PriceDecayService(ProductRepository productRepository, PriceHistoryRepository priceHistoryRepository, PricingRedisRepository redisRepository, PriceBroadcastService broadcastService) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.redisRepository = redisRepository;
        this.broadcastService = broadcastService;
    }

    /**
     * Checks products every 30 seconds for decay eligibility (no trades for decayIntervalSeconds)
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void checkForPriceDecay() {
        LocalDateTime now = LocalDateTime.now();
        List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
        List<ProductPriceDTO> decayedDTOs = new ArrayList<>();
        boolean priceChanged = false;

        int currentMarketVersion = redisRepository.getMarketVersion();

        for (Product product : products) {
            LocalDateTime lastTrade = product.getLastPriceChangeTimestamp();

            boolean isEligibleForDecay = (lastTrade == null) || now.isAfter(lastTrade.plusSeconds(decayIntervalSeconds));

            if (isEligibleForDecay) {
                BigDecimal currentPrice = product.getCurrentCupPrice() != null ? product.getCurrentCupPrice() : product.getDefaultCupPrice();
                BigDecimal minPrice = product.getMinCupPrice() != null ? product.getMinCupPrice() : new BigDecimal("18.00");

                if (currentPrice.compareTo(minPrice) > 0) {
                    BigDecimal newPrice = currentPrice.subtract(decayStep).max(minPrice);
                    BigDecimal priceDelta = newPrice.subtract(currentPrice);

                    product.setCurrentCupPrice(newPrice);
                    product.setPriceVersion((product.getPriceVersion() != null ? product.getPriceVersion() : 1) + 1);
                    product.setLastPriceChangeTimestamp(now);
                    productRepository.saveAndFlush(product);

                    redisRepository.setProductPrice(product.getId(), newPrice);
                    redisRepository.setLastTradeTimestamp(product.getId(), now);

                    PriceHistory history = PriceHistory.builder()
                            .productId(product.getId())
                            .oldPrice(currentPrice)
                            .newPrice(newPrice)
                            .priceChange(priceDelta)
                            .reason("PRICE_DECAY")
                            .explanation("Zero-demand price decay for " + product.getName())
                            .calculationWindowStart(now.minusSeconds(decayIntervalSeconds))
                            .calculationWindowEnd(now)
                            .rawW0(0)
                            .rawW1(0)
                            .rawW2(0)
                            .unconsumedW0(0)
                            .weightedSales(0.0)
                            .targetSales(product.getTargetSalesPer2Minute() != null ? product.getTargetSalesPer2Minute() : 1.0)
                            .demandRatio(0.0)
                            .priceVersion(product.getPriceVersion())
                            .createdAt(now)
                            .build();

                    priceHistoryRepository.save(history);

                    BigDecimal baseP = product.getDefaultCupPrice() != null ? product.getDefaultCupPrice() : new BigDecimal("25.00");
                    double changePct = ((newPrice.subtract(baseP)).doubleValue() / baseP.doubleValue()) * 100.0;

                    ProductPriceDTO dto = ProductPriceDTO.builder()
                            .beverageId(product.getId())
                            .name(product.getName())
                            .flavour(product.getFlavour())
                            .currentPrice(newPrice)
                            .effectivePrice(newPrice)
                            .previousPrice(currentPrice)
                            .priceDelta(priceDelta)
                            .priceVersion(product.getPriceVersion())
                            .priceChangePct(changePct)
                            .trendDirection("DOWN")
                            .demandRatio(0.0)
                            .weightedSales(0.0)
                            .targetSales(product.getTargetSalesPer2Minute() != null ? product.getTargetSalesPer2Minute() : 1.0)
                            .demandLevelCategory("ZERO_DEMAND_DECAY")
                            .minCupPrice(product.getMinCupPrice())
                            .maxCupPrice(product.getMaxCupPrice())
                            .build();

                    decayedDTOs.add(dto);
                    priceChanged = true;
                    log.info("📉 [PRICE DECAY] ProductId={} ({}) decayed ₹{} -> ₹{} due to zero trades for >{}s",
                            product.getId(), product.getName(), currentPrice, newPrice, decayIntervalSeconds);
                }
            }
        }

        if (priceChanged && !decayedDTOs.isEmpty()) {
            int newMarketVersion = redisRepository.incrementMarketVersion();
            broadcastService.broadcastPriceUpdate(newMarketVersion, decayedDTOs);
        }
    }
}
