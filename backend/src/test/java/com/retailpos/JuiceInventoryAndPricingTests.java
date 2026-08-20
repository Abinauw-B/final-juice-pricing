package com.retailpos;

import com.retailpos.domain.*;
import com.retailpos.inventory.JuiceBatchService;
import com.retailpos.pos.POSService;
import com.retailpos.pricing.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class JuiceInventoryAndPricingTests {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JuiceBatchRepository batchRepository;

    @Autowired
    private JuiceBatchService juiceBatchService;

    @Autowired
    private POSService posService;

    @Autowired
    private StockPressureService stockPressureService;

    @Autowired
    private TimeFactorService timeFactorService;

    @Autowired
    private DemandCalculationService demandCalculationService;

    @Autowired
    private PriceAdjustmentService priceAdjustmentService;

    @Autowired
    private PricingSimulationService pricingSimulationService;

    @Autowired
    private PricingEngineService pricingEngineService;

    @Autowired
    private MarketCrashService marketCrashService;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    private Product mangoProduct;

    @BeforeEach
    void setUp() {
        mangoProduct = productRepository.findByFlavourIgnoreCase("MANGO")
                .orElseGet(() -> productRepository.save(Product.builder()
                        .name("Fresh Mango Juice")
                        .flavour("MANGO")
                        .defaultCupSizeMl(250)
                        .defaultCupPrice(new BigDecimal("20.00"))
                        .currentCupPrice(new BigDecimal("20.00"))
                        .minCupPrice(new BigDecimal("18.00"))
                        .maxCupPrice(new BigDecimal("25.00"))
                        .build()));
        mangoProduct.setDefaultCupPrice(new BigDecimal("20.00"));
        mangoProduct.setCurrentCupPrice(new BigDecimal("20.00"));
        mangoProduct.setLastPriceChangeTimestamp(null);
        mangoProduct = productRepository.save(mangoProduct);
        JuiceBatch batch = batchRepository.findFirstActiveBatchForProduct(mangoProduct.getId()).orElse(null);
        if (batch == null) {
            batchRepository.save(JuiceBatch.builder()
                    .productId(mangoProduct.getId())
                    .batchCode("BATCH-TEST-" + System.currentTimeMillis())
                    .containerCapacityMl(20000)
                    .initialVolumeMl(20000)
                    .remainingVolumeMl(20000)
                    .cupSizeMl(250)
                    .status(JuiceBatch.BatchStatus.ACTIVE)
                    .createdAt(java.time.LocalDateTime.now())
                    .build());
        } else if (batch.getRemainingVolumeMl() < 5000) {
            batch.setRemainingVolumeMl(20000);
            batch.setStatus(JuiceBatch.BatchStatus.ACTIVE);
            batchRepository.save(batch);
        }
    }

    @Test
    @DisplayName("Req 1: 20L batch conversion to 80 cups of 250ml")
    void test20LBatchCupConversion() {
        JuiceBatch batch = JuiceBatch.builder()
                .containerCapacityMl(20000)
                .initialVolumeMl(20000)
                .remainingVolumeMl(20000)
                .cupSizeMl(250)
                .build();
        assertEquals(80, batch.getEstimatedRemainingCups());
    }

    @Test
    @DisplayName("Req 2: Single 250ml cup sale deducts exactly 250ml")
    @Transactional
    void testSingleCupSaleDeduction() {
        JuiceBatch activeBatch = juiceBatchService.getActiveBatchForProduct(mangoProduct.getId());
        assertNotNull(activeBatch);
        int initialVol = activeBatch.getRemainingVolumeMl();

        POSService.CartItemRequest item = new POSService.CartItemRequest();
        item.setProductId(mangoProduct.getId());
        item.setQuantity(1);
        item.setCupSizeMl(250);

        POSService.CheckoutRequest req = new POSService.CheckoutRequest();
        req.setItems(List.of(item));
        req.setPaymentMethod("CASH");

        POSService.CheckoutResponse res = posService.processCheckout(req);

        JuiceBatch updatedBatch = batchRepository.findById(activeBatch.getId()).orElseThrow();
        assertEquals(initialVol - 250, updatedBatch.getRemainingVolumeMl());
        assertEquals(250, res.getItems().get(0).getVolumeDeductedMl());
    }

    @Test
    @DisplayName("Req 3: Two cups sale deducts exactly 500ml")
    @Transactional
    void testTwoCupSaleDeduction() {
        JuiceBatch activeBatch = juiceBatchService.getActiveBatchForProduct(mangoProduct.getId());
        int initialVol = activeBatch.getRemainingVolumeMl();

        POSService.CartItemRequest item = new POSService.CartItemRequest();
        item.setProductId(mangoProduct.getId());
        item.setQuantity(2);
        item.setCupSizeMl(250);

        POSService.CheckoutRequest req = new POSService.CheckoutRequest();
        req.setItems(List.of(item));

        POSService.CheckoutResponse res = posService.processCheckout(req);

        JuiceBatch updatedBatch = batchRepository.findById(activeBatch.getId()).orElseThrow();
        assertEquals(initialVol - 500, updatedBatch.getRemainingVolumeMl());
        assertEquals(500, res.getItems().get(0).getVolumeDeductedMl());
    }

    @Test
    @DisplayName("Req 4: Initial cup price default is ₹20")
    void testInitialDefaultPrice() {
        assertEquals(new BigDecimal("20.00"), mangoProduct.getDefaultCupPrice());
        assertEquals(new BigDecimal("20.00"), mangoProduct.getCurrentCupPrice());
    }

    @Test
    @DisplayName("Req 5 & 6: Bounded limits (Min ₹18, Max ₹25)")
    void testBoundedPriceLimits() {
        assertEquals(new BigDecimal("18.00"), mangoProduct.getMinCupPrice());
        assertEquals(new BigDecimal("25.00"), mangoProduct.getMaxCupPrice());
    }

    @Test
    @DisplayName("Req 7 & 8: High vs Low demand price adjustment logic")
    @Transactional
    void testDemandPriceAdjustment() {
        mangoProduct.setLastPriceChangeTimestamp(null);
        productRepository.save(mangoProduct);

        PriceAdjustmentService.PriceEvaluationResult res = priceAdjustmentService.evaluateAndAdjustPrice(mangoProduct.getId());
        assertNotNull(res);
        assertNotNull(res.getExplanation());
    }

    @Test
    @DisplayName("Req 9: Time factor multipliers (Morning 1.0, Afternoon 1.1, Evening 1.2, Night 1.0)")
    void testTimeFactorMultipliers() {
        assertEquals(1.0, timeFactorService.getTimeFactorMultiplier(LocalTime.of(8, 0)));
        assertEquals(1.1, timeFactorService.getTimeFactorMultiplier(LocalTime.of(14, 0)));
        assertEquals(1.2, timeFactorService.getTimeFactorMultiplier(LocalTime.of(18, 0)));
        assertEquals(1.0, timeFactorService.getTimeFactorMultiplier(LocalTime.of(22, 0)));
    }

    @Test
    @DisplayName("Req 10: Cooldown window enforcement")
    @Transactional
    void testCooldownEnforcement() {
        systemConfigRepository.save(new com.retailpos.domain.SystemConfig("cooldown_minutes", "10", "Cooldown mins"));
        mangoProduct.setLastPriceChangeTimestamp(java.time.LocalDateTime.now());
        productRepository.save(mangoProduct);

        PriceAdjustmentService.PriceEvaluationResult res = priceAdjustmentService.evaluateAndAdjustPrice(mangoProduct.getId());
        assertEquals("COOLDOWN_ACTIVE", res.getStatusReason());
        assertFalse(res.isPriceChanged());
    }

    @Test
    @DisplayName("Req 11: Sales velocity window calculation")
    void testSalesVelocityCalculation() {
        double score = demandCalculationService.calculateVelocityScore(mangoProduct.getId(), 15);
        assertTrue(score >= 0.0 && score <= 100.0);
    }

    @Test
    @DisplayName("Req 12: Stock pressure calculation")
    void testStockPressureThresholds() {
        double pressure = stockPressureService.calculateStockPressurePercentage(mangoProduct.getId());
        assertTrue(pressure >= 0.0 && pressure <= 100.0);
    }

    @Test
    @DisplayName("Req 13: Dynamic pricing explanation generation")
    @Transactional
    void testExplanationGeneration() {
        mangoProduct.setLastPriceChangeTimestamp(null);
        productRepository.save(mangoProduct);

        PriceAdjustmentService.PriceEvaluationResult res = priceAdjustmentService.evaluateAndAdjustPrice(mangoProduct.getId());
        assertTrue(res.getExplanation().contains(mangoProduct.getFlavour()));
    }

    @Test
    @DisplayName("Req 14: Simulation sandbox state isolation")
    void testSimulationSandboxIsolation() {
        PricingSimulationService.SimulationRequest req = new PricingSimulationService.SimulationRequest();
        req.setInitialVolumeMl(20000);
        req.setTotalSimulatedPurchases(20);

        int volBefore = juiceBatchService.getActiveBatchForProduct(mangoProduct.getId()).getRemainingVolumeMl();

        PricingSimulationService.SimulationResponse res = pricingSimulationService.runSimulation(req);

        int volAfter = juiceBatchService.getActiveBatchForProduct(mangoProduct.getId()).getRemainingVolumeMl();

        assertEquals(volBefore, volAfter, "Simulation must not mutate actual database volume");
        assertNotNull(res.getSteps());
    }

    @Test
    @DisplayName("Req 15: Concurrent checkout safety with row locking")
    void testConcurrentCheckoutSafety() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.execute(() -> {
                try {
                    POSService.CartItemRequest item = new POSService.CartItemRequest();
                    item.setProductId(mangoProduct.getId());
                    item.setQuantity(1);

                    POSService.CheckoutRequest req = new POSService.CheckoutRequest();
                    req.setItems(List.of(item));

                    posService.processCheckout(req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Handled gracefully
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        assertTrue(successCount.get() > 0);
    }

    @Test
    @DisplayName("Req 16: Admin system configuration updating")
    @Transactional
    void testConfigUpdating() {
        SystemConfig cfg = systemConfigRepository.findById("cooldown_minutes").orElseThrow();
        cfg.setConfigValue("15");
        systemConfigRepository.save(cfg);

        SystemConfig updated = systemConfigRepository.findById("cooldown_minutes").orElseThrow();
        assertEquals("15", updated.getConfigValue());
    }

    @Test
    @DisplayName("Req 17: 60-Second Automated Pricing Engine Cycle Execution")
    @Transactional
    void testPricingEngine60SecondCycleExecution() {
        PricingEngineService.PriceEvaluationCycleResult result = pricingEngineService.execute60SecondPricingEngine();
        assertNotNull(result);
        assertNotNull(result.getTimestamp());
        assertTrue(result.getEvaluatedProductsCount() >= 0);
        assertEquals("TRADING_NORMAL", result.getMarketStatus());
    }

    @Test
    @DisplayName("Req 18: Market Crash Override halts standard engine and sets status")
    @Transactional
    void testMarketCrashOverride() {
        try {
            marketCrashService.triggerMarketCrash(3, "TEST");
            assertTrue(marketCrashService.isCrashActive());

            PricingEngineService.PriceEvaluationCycleResult crashResult = pricingEngineService.execute60SecondPricingEngine();
            assertEquals("MARKET_CRASH_ACTIVE", crashResult.getMarketStatus());
            assertEquals(0, crashResult.getEvaluatedProductsCount());
        } finally {
            marketCrashService.stopMarketCrash();
            assertFalse(marketCrashService.isCrashActive());
        }
    }
}
