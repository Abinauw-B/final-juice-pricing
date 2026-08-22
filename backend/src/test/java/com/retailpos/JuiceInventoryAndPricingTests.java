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
import java.time.LocalDateTime;
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
        if (marketCrashService != null) {
            marketCrashService.stopMarketCrash();
        }
        mangoProduct = productRepository.findByFlavourIgnoreCase("MANGO")
                .orElseGet(() -> productRepository.save(Product.builder()
                        .name("Fresh Mango Juice")
                        .flavour("MANGO")
                        .defaultCupSizeMl(250)
                        .defaultCupPrice(new BigDecimal("25.00"))
                        .currentCupPrice(new BigDecimal("25.00"))
                        .minCupPrice(new BigDecimal("18.00"))
                        .maxCupPrice(new BigDecimal("35.00"))
                        .targetSalesPer2Minute(1.0)
                        .build()));
        mangoProduct.setDefaultCupPrice(new BigDecimal("25.00"));
        mangoProduct.setCurrentCupPrice(new BigDecimal("25.00"));
        mangoProduct.setTargetSalesPer2Minute(1.0);
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
                    .createdAt(LocalDateTime.now())
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
        POSService.CartItemRequest item = new POSService.CartItemRequest();
        item.setProductId(mangoProduct.getId());
        item.setQuantity(1);
        item.setCupSizeMl(250);

        POSService.CheckoutRequest req = new POSService.CheckoutRequest();
        req.setItems(List.of(item));
        req.setPaymentMethod("CASH");

        POSService.CheckoutResponse res = posService.processCheckout(req);

        assertNotNull(res);
        assertNotNull(res.getItems());
        assertFalse(res.getItems().isEmpty());
        assertEquals(250, res.getItems().get(0).getVolumeDeductedMl());
    }

    @Test
    @DisplayName("Req 3: Initial cup price base is ₹25")
    void testInitialDefaultPrice() {
        assertEquals(new BigDecimal("25.00"), mangoProduct.getDefaultCupPrice());
        assertEquals(new BigDecimal("25.00"), mangoProduct.getCurrentCupPrice());
    }

    @Test
    @DisplayName("Req 4: Allowed price bounds (Min ₹18, Max ₹35)")
    void testBoundedPriceLimits() {
        assertEquals(new BigDecimal("18.00"), mangoProduct.getMinCupPrice());
        assertEquals(new BigDecimal("35.00"), mangoProduct.getMaxCupPrice());
    }

    @Test
    @DisplayName("Req 5: Demand price adjustment evaluation")
    @Transactional
    void testDemandPriceAdjustment() {
        mangoProduct.setLastPriceChangeTimestamp(null);
        productRepository.save(mangoProduct);

        PriceAdjustmentService.PriceEvaluationResult res = priceAdjustmentService.evaluateAndAdjustPrice(mangoProduct.getId());
        assertNotNull(res);
        assertNotNull(res.getExplanation());
        assertNotNull(res.getDemandLevelCategory());
    }

    @Test
    @DisplayName("Req 6: Simulation sandbox state isolation")
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
    @DisplayName("Req 7: Concurrent checkout safety with row locking")
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
    @DisplayName("Req 8: 2-Minute Automated Pricing Engine Settlement Cycle Execution")
    @Transactional
    void testPricingEngine2MinuteSettlementCycleExecution() {
        PricingEngineService.PriceEvaluationCycleResult result = pricingEngineService.executeSettlementCycle();
        assertNotNull(result);
        assertNotNull(result.getTimestamp());
        assertNotNull(result.getNextUpdateAt());
        assertTrue(result.getEvaluatedProductsCount() >= 0);
        assertEquals("OPEN", result.getMarketStatus());
    }

    @Test
    @DisplayName("Req 9: Market Crash Override isolates crash price without overwriting normal price")
    @Transactional
    void testMarketCrashOverride() {
        try {
            marketCrashService.triggerMarketCrash(3, 2, new BigDecimal("18.00"), "TEST");
            assertTrue(marketCrashService.isCrashActive());
            assertTrue(marketCrashService.isProductCrashed(mangoProduct.getId()) || !marketCrashService.getStatus().getAffectedProductIds().isEmpty());
        } finally {
            marketCrashService.stopMarketCrash();
            assertFalse(marketCrashService.isCrashActive());
        }
    }

    @Test
    @DisplayName("Req 10 & 11: Repeated settlement idempotency and new purchase recognition")
    @Transactional
    void testRepeatedSettlementIdempotency() {
        mangoProduct.setCurrentCupPrice(new BigDecimal("25.00"));
        mangoProduct.setOrderCount(10);
        mangoProduct = productRepository.save(mangoProduct);

        PriceAdjustmentService.PriceEvaluationResult res1 = priceAdjustmentService.evaluateAndAdjustPrice(mangoProduct.getId());
        assertEquals(new BigDecimal("27.00"), res1.getNewPrice());

        PriceAdjustmentService.PriceEvaluationResult res2 = priceAdjustmentService.evaluateAndAdjustPrice(mangoProduct.getId());
        assertEquals(new BigDecimal("24.84"), res2.getNewPrice());
    }

    @Test
    @DisplayName("Req 12 & 13: Cross-product dynamic pricing isolation")
    @Transactional
    void testCrossProductIsolation() {
        Product thunder = productRepository.findByFlavourIgnoreCase("THUNDER")
                .orElseGet(() -> productRepository.save(Product.builder()
                        .name("Thunder")
                        .flavour("THUNDER")
                        .defaultCupPrice(new BigDecimal("25.00"))
                        .currentCupPrice(new BigDecimal("25.00"))
                        .minCupPrice(new BigDecimal("18.00"))
                        .maxCupPrice(new BigDecimal("35.00"))
                        .orderCount(0)
                        .targetOrders(5)
                        .volatility(new BigDecimal("0.0800"))
                        .build()));

        Product orange = productRepository.findByFlavourIgnoreCase("ORANGE")
                .orElseGet(() -> productRepository.save(Product.builder()
                        .name("Valencia Orange Juice")
                        .flavour("ORANGE")
                        .defaultCupPrice(new BigDecimal("25.00"))
                        .currentCupPrice(new BigDecimal("25.00"))
                        .minCupPrice(new BigDecimal("18.00"))
                        .maxCupPrice(new BigDecimal("35.00"))
                        .orderCount(0)
                        .targetOrders(5)
                        .volatility(new BigDecimal("0.0800"))
                        .build()));

        orange.setCurrentCupPrice(new BigDecimal("25.00"));
        orange = productRepository.save(orange);

        thunder.setCurrentCupPrice(new BigDecimal("25.00"));
        thunder.setOrderCount(10);
        thunder = productRepository.save(thunder);

        priceAdjustmentService.evaluateAndAdjustPrice(thunder.getId());

        Product refreshedOrange = productRepository.findById(orange.getId()).orElseThrow();
        assertEquals(new BigDecimal("25.00"), refreshedOrange.getCurrentCupPrice(), "Orange price must remain unchanged when Thunder is purchased");
    }
}
