package com.retailpos.pricing.service;

import com.retailpos.domain.Product;
import com.retailpos.domain.ProductRepository;
import com.retailpos.pos.POSService;
import com.retailpos.pricing.MarketCrashService;
import com.retailpos.pricing.PriceAdjustmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LiveMarketSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(LiveMarketSimulatorService.class);

    private final ProductRepository productRepository;
    private final POSService posService;
    private final MarketCrashService marketCrashService;

    private volatile boolean enabled = true;
    private final AtomicLong simulatedOrdersCount = new AtomicLong(0);
    private volatile LocalDateTime lastOrderTime = null;
    private final Random random = new Random();

    public LiveMarketSimulatorService(ProductRepository productRepository,
                                      POSService posService,
                                      MarketCrashService marketCrashService) {
        this.productRepository = productRepository;
        this.posService = posService;
        this.marketCrashService = marketCrashService;
        log.info("[LIVE_MARKET_SIMULATOR] Initialized. Autonomous trading simulation is ENABLED by default.");
    }

    /**
     * Periodically executes simulated pub exchange transactions every 12 seconds.
     * This drives authentic cup volume across DWMA time windows (W0, W1, W2),
     * enabling live surges and decays on dynamic beverages.
     */
    @Scheduled(fixedDelay = 12000, initialDelay = 10000)
    public void simulateLiveMarketTrades() {
        if (!enabled) {
            return;
        }

        if (PriceAdjustmentService.isMarketPaused()) {
            return;
        }

        if (marketCrashService != null && marketCrashService.isCrashActive()) {
            return;
        }

        try {
            List<Product> allProducts = productRepository.findByIsActiveTrueOrderByIdAsc();
            if (allProducts.isEmpty()) {
                return;
            }

            // Target dynamic juices
            List<Product> dynamicProducts = allProducts.stream()
                    .filter(p -> p.getPricingMode() == null || !"FIXED".equalsIgnoreCase(p.getPricingMode()))
                    .toList();

            List<Product> candidatePool = dynamicProducts.isEmpty() ? allProducts : dynamicProducts;

            // Pick 1 product (or occasionally 2 for market rush)
            int pickCount = (random.nextDouble() < 0.25) ? 2 : 1;
            List<POSService.CartItemRequest> items = new ArrayList<>();

            for (int i = 0; i < pickCount; i++) {
                Product selected = candidatePool.get(random.nextInt(candidatePool.size()));
                int qty = random.nextInt(2) + 1; // 1 or 2 cups
                int cupSize = (selected.getDefaultCupSizeMl() != null && selected.getDefaultCupSizeMl() > 0)
                        ? selected.getDefaultCupSizeMl() : 250;

                items.add(new POSService.CartItemRequest(selected.getId(), qty, cupSize));
            }

            String simKey = "SIM-BOT-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
            POSService.CheckoutRequest checkoutRequest = new POSService.CheckoutRequest(
                    items,
                    "BOT_CASH",
                    simKey,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );

            POSService.CheckoutResponse res = posService.processCheckout(checkoutRequest);
            if (res != null && res.isSuccess()) {
                simulatedOrdersCount.incrementAndGet();
                lastOrderTime = LocalDateTime.now();
                log.info("[LIVE_MARKET_SIMULATOR] Order #{} placed (ORD: {}). Drinks traded: {}",
                        simulatedOrdersCount.get(), res.getOrderNumber(),
                        items.stream().map(it -> "prodId=" + it.getProductId() + " qty=" + it.getQuantity()).toList());
            }
        } catch (Exception e) {
            log.warn("[LIVE_MARKET_SIMULATOR] Simulated order non-fatal exception: {}", e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> toggle(Boolean requestedState) {
        if (requestedState != null) {
            this.enabled = requestedState;
        } else {
            this.enabled = !this.enabled;
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("enabled", this.enabled);
        res.put("simulatedOrdersCount", simulatedOrdersCount.get());
        res.put("lastOrderTime", lastOrderTime != null ? lastOrderTime.toString() : null);
        log.info("[LIVE_MARKET_SIMULATOR] Trading Bot state toggled to: {}", this.enabled ? "ENABLED" : "DISABLED");
        return res;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> res = new HashMap<>();
        res.put("enabled", this.enabled);
        res.put("intervalSeconds", 12);
        res.put("simulatedOrdersCount", simulatedOrdersCount.get());
        res.put("lastOrderTime", lastOrderTime != null ? lastOrderTime.toString() : null);
        return res;
    }
}
