package com.retailpos.pricing.service;

import com.retailpos.domain.Product;
import com.retailpos.domain.ProductRepository;
import com.retailpos.pricing.MarketCrashService;
import com.retailpos.pricing.PriceAdjustmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LiveMarketSimulatorService — Synthetic demand generation for the Mojito Exchange.
 *
 * PHASE 8 PRODUCTION ISOLATION FIX:
 * Previously, this service called posService.processCheckout(), which caused:
 *   1. Real physical inventory deduction from production JuiceBatch records
 *   2. Real financial orders recorded in sales_orders / sales_order_items
 *   3. Contamination of revenue and stock reports with bot transactions
 *
 * The simulator now drives the DWMA pricing engine ONLY by atomically incrementing
 * product.orderCount (the exact field that PriceAdjustmentService reads when computing
 * W0, W1, W2 demand windows). No inventory, no orders, no financial records are touched.
 *
 * The pricing algorithm is unaffected — the signal path is identical.
 */
@Service
public class LiveMarketSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(LiveMarketSimulatorService.class);

    private final ProductRepository productRepository;
    private final MarketCrashService marketCrashService;

    private volatile boolean enabled = false;
    private final AtomicLong simulatedOrdersCount = new AtomicLong(0);
    private volatile LocalDateTime lastOrderTime = null;
    private final Random random = new Random();

    public LiveMarketSimulatorService(ProductRepository productRepository,
                                      MarketCrashService marketCrashService,
                                      @org.springframework.beans.factory.annotation.Value("${market.simulator.enabled:false}") boolean defaultEnabled) {
        this.productRepository = productRepository;
        this.marketCrashService = marketCrashService;
        this.enabled = defaultEnabled;
        log.info("[LIVE_MARKET_SIMULATOR] Initialized. Autonomous simulation is {} (Config: market.simulator.enabled={})",
                this.enabled ? "ENABLED" : "DISABLED", defaultEnabled);
    }

    /**
     * Periodically generates synthetic demand signals every 12 seconds.
     *
     * ISOLATION: Only increments product.orderCount via an atomic SQL UPDATE.
     * No inventory is deducted. No sales records are created.
     * The DWMA engine reads orderCount to calculate demand windows (W0, W1, W2).
     */
    @Scheduled(fixedDelay = 12000, initialDelay = 10000)
    @Transactional
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

            // Target dynamic-mode products only (skip FIXED / MANUAL_LOCK)
            List<Product> dynamicProducts = allProducts.stream()
                    .filter(p -> p.getPricingMode() == null || (!"FIXED".equalsIgnoreCase(p.getPricingMode())
                            && !"MANUAL_OVERRIDE".equalsIgnoreCase(p.getPricingMode())
                            && !"MANUAL_LOCK".equalsIgnoreCase(p.getPricingMode())
                            && !"LOCKED".equalsIgnoreCase(p.getPricingMode())))
                    .toList();

            List<Product> candidatePool = dynamicProducts.isEmpty() ? allProducts : dynamicProducts;

            // Pick 1 product (or occasionally 2 for simulated market rush)
            int pickCount = (random.nextDouble() < 0.25) ? 2 : 1;
            List<String> tradeLog = new ArrayList<>();

            for (int i = 0; i < pickCount; i++) {
                Product selected = candidatePool.get(random.nextInt(candidatePool.size()));
                int qty = random.nextInt(2) + 1; // 1 or 2 cups

                // PHASE 8 ISOLATION: Directly increment orderCount using atomic SQL UPDATE.
                // This is exactly what the DWMA engine reads when calculating demand ratios.
                // No POSService, no inventory deduction, no financial orders created.
                productRepository.incrementOrderCount(selected.getId(), qty);

                tradeLog.add("prodId=" + selected.getId() + " qty=" + qty);
            }

            simulatedOrdersCount.incrementAndGet();
            lastOrderTime = LocalDateTime.now();
            log.info("[LIVE_MARKET_SIMULATOR] Synthetic demand signal #{} emitted. Products: {}",
                    simulatedOrdersCount.get(), tradeLog);

        } catch (Exception e) {
            log.warn("[LIVE_MARKET_SIMULATOR] Simulation tick error (non-fatal): {}", e.getMessage());
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
        res.put("mode", "ISOLATED — demand signal only, no inventory or order records");
        log.info("[LIVE_MARKET_SIMULATOR] Trading Bot state toggled to: {}", this.enabled ? "ENABLED" : "DISABLED");
        return res;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> res = new HashMap<>();
        res.put("enabled", this.enabled);
        res.put("intervalSeconds", 12);
        res.put("simulatedOrdersCount", simulatedOrdersCount.get());
        res.put("lastOrderTime", lastOrderTime != null ? lastOrderTime.toString() : null);
        res.put("mode", "ISOLATED — demand signal only, no inventory or order records");
        return res;
    }
}
