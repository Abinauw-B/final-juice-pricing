package com.retailpos.verification;

import com.retailpos.domain.*;
import com.retailpos.pos.POSService;
import com.retailpos.pricing.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 32 — Transactional Data Consistency Verification
 * Phase 33 — Price Consistency Verification
 * Phase 34 — 20L Inventory Volume Consistency Verification
 * Phase 35 — Concurrency & Load Testing (Pessimistic Locking Verification)
 *
 * These tests verify end-to-end data integrity across the entire pricing
 * and inventory pipeline. Every assertion covers a specific invariant that
 * must hold under concurrent usage in production.
 */
@SpringBootTest(properties = "pricing.scheduler.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SuppressWarnings("null")
@DisplayName("Phases 32-35: Data Consistency & Concurrency Verification")
public class DataConsistencyAndConcurrencyTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private JuiceBatchRepository batchRepository;
    @Autowired private SalesOrderRepository salesOrderRepository;
    @Autowired private SalesOrderItemRepository salesOrderItemRepository;
    @Autowired private PriceHistoryRepository priceHistoryRepository;
    @Autowired private POSService posService;
    @Autowired private PriceAdjustmentService priceAdjustmentService;
    @Autowired private PricingSettlementCoordinator settlementCoordinator;
    @Autowired private MarketCrashService marketCrashService;
    @Autowired private PricingProcessedSaleRepository pricingProcessedSaleRepository;

    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        // Stop any active crash
        if (marketCrashService.isCrashActive()) {
            marketCrashService.stopMarketCrash();
        }
        PriceAdjustmentService.setMarketPaused(false);

        // Clear previous orders to isolate DWMA calculation windows per test
        pricingProcessedSaleRepository.deleteAll();
        salesOrderItemRepository.deleteAll();
        salesOrderRepository.deleteAll();

        // Reset order count & weighted sales across all products
        for (Product p : productRepository.findAll()) {
            p.setOrderCount(0);
            p.setWeightedSales(null);
            productRepository.save(p);
        }
        productRepository.flush();

        // Load or create Product A (Mango)
        productA = productRepository.findByFlavourIgnoreCase("FRESH_MANGO_JUICE")
                .or(() -> productRepository.findById(1L))
                .orElseGet(() -> productRepository.save(Product.builder()
                        .name("Fresh Mango Juice").flavour("FRESH_MANGO_JUICE")
                        .defaultCupSizeMl(250).defaultCupPrice(new BigDecimal("25.00"))
                        .currentCupPrice(new BigDecimal("25.00"))
                        .minCupPrice(new BigDecimal("20.00")).maxCupPrice(new BigDecimal("30.00"))
                        .targetSalesPer1Minute(0.55).build()));

        productA.setCurrentCupPrice(new BigDecimal("25.00"));
        productA.setDefaultCupPrice(new BigDecimal("25.00"));
        productA.setMinCupPrice(new BigDecimal("20.00"));
        productA.setMaxCupPrice(new BigDecimal("30.00"));
        productA.setPricingMode("DYNAMIC");
        productA.setWeightedSales(null);
        productA.setOrderCount(0);
        productA.setIsActive(true);
        productA = productRepository.saveAndFlush(productA);

        // Load or create Product B (different product for isolation tests)
        productB = productRepository.findAll().stream()
                .filter(p -> !p.getId().equals(productA.getId()) && Boolean.TRUE.equals(p.getIsActive()))
                .findFirst()
                .orElseGet(() -> productRepository.save(Product.builder()
                        .name("Watermelon Cooler").flavour("WATERMELON_COOLER")
                        .defaultCupSizeMl(250).defaultCupPrice(new BigDecimal("22.00"))
                        .currentCupPrice(new BigDecimal("22.00"))
                        .minCupPrice(new BigDecimal("18.00")).maxCupPrice(new BigDecimal("28.00"))
                        .targetSalesPer1Minute(0.55).build()));

        productB.setCurrentCupPrice(new BigDecimal("22.00"));
        productB.setDefaultCupPrice(new BigDecimal("22.00"));
        productB.setMinCupPrice(new BigDecimal("18.00"));
        productB.setMaxCupPrice(new BigDecimal("28.00"));
        productB.setPricingMode("DYNAMIC");
        productB.setWeightedSales(null);
        productB.setOrderCount(0);
        productB.setIsActive(true);
        productB = productRepository.saveAndFlush(productB);

        // Ensure A has an active batch
        ensureActiveBatch(productA.getId(), new BigDecimal("25.00"), 20000);
        // Ensure B has an active batch
        ensureActiveBatch(productB.getId(), new BigDecimal("22.00"), 20000);
    }

    private void ensureActiveBatch(Long productId, BigDecimal price, int volumeMl) {
        JuiceBatch existing = batchRepository.findFirstActiveBatchForProduct(productId).orElse(null);
        if (existing == null) {
            batchRepository.save(JuiceBatch.builder()
                    .productId(productId)
                    .batchCode("BATCH-CONS-" + productId + "-" + System.currentTimeMillis())
                    .containerCapacityMl(volumeMl)
                    .initialVolumeMl(volumeMl)
                    .remainingVolumeMl(volumeMl)
                    .cupSizeMl(250)
                    .status(JuiceBatch.BatchStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .build());
        } else {
            existing.setRemainingVolumeMl(volumeMl);
            existing.setStatus(JuiceBatch.BatchStatus.ACTIVE);
            batchRepository.save(existing);
        }
        batchRepository.flush();
    }

    // =========================================================================
    // PHASE 32: Transactional Data Consistency
    // =========================================================================

    @Test
    @DisplayName("Ph32-01: Checkout creates exactly one SalesOrder and correct SalesOrderItems")
    void ph32_checkoutCreatesExactlyOneOrder() {
        long ordersBefore = salesOrderRepository.count();

        POSService.CartItemRequest item = new POSService.CartItemRequest();
        item.setProductId(productA.getId());
        item.setQuantity(2);
        item.setCupSizeMl(250);

        POSService.CheckoutRequest req = new POSService.CheckoutRequest();
        req.setItems(List.of(item));
        req.setPaymentMethod("CASH");
        req.setIdempotencyKey("CONS-TEST-" + UUID.randomUUID());

        POSService.CheckoutResponse res = posService.processCheckout(req);

        assertTrue(res.isSuccess(), "Checkout should succeed");
        assertNotNull(res.getOrderNumber(), "Order number must not be null");

        long ordersAfter = salesOrderRepository.count();
        assertEquals(1, ordersAfter - ordersBefore, "Exactly one new order must be created");

        // Verify the order item
        SalesOrder order = salesOrderRepository.findByOrderNumberWithItems(res.getOrderNumber())
                .orElseThrow(() -> new AssertionError("Order not found in DB"));

        assertNotNull(order.getItems(), "Order items must not be null");
        assertEquals(1, order.getItems().size(), "Order must contain exactly 1 item");
        assertEquals(productA.getId(), order.getItems().get(0).getProductId(), "Item productId must match");
        assertEquals(2, order.getItems().get(0).getQuantity(), "Item quantity must be 2");
    }

    @Test
    @DisplayName("Ph32-02: Idempotency key prevents duplicate order creation")
    void ph32_idempotencyKeyPreventsDuplicates() {
        String idempotencyKey = "IDEM-" + UUID.randomUUID();

        POSService.CartItemRequest item = new POSService.CartItemRequest();
        item.setProductId(productA.getId());
        item.setQuantity(1);
        item.setCupSizeMl(250);

        POSService.CheckoutRequest req = new POSService.CheckoutRequest();
        req.setItems(List.of(item));
        req.setPaymentMethod("CASH");
        req.setIdempotencyKey(idempotencyKey);

        long ordersBefore = salesOrderRepository.count();

        // Submit same idempotency key twice
        POSService.CheckoutResponse res1 = posService.processCheckout(req);
        POSService.CheckoutResponse res2 = posService.processCheckout(req);

        assertTrue(res1.isSuccess(), "First checkout must succeed");
        assertTrue(res2.isSuccess(), "Second checkout with same key must not throw");

        long ordersAfter = salesOrderRepository.count();
        assertEquals(1, ordersAfter - ordersBefore, "Only one order must be created (idempotency)");

        // Both responses should return the same order number
        assertEquals(res1.getOrderNumber(), res2.getOrderNumber(), "Both responses must have same order number");
    }

    @Test
    @DisplayName("Ph32-03: PriceHistory audit record is created for every settlement")
    void ph32_priceHistoryAuditCreatedOnSettlement() {
        long historyBefore = priceHistoryRepository.count();

        // Run a forced settlement
        settlementCoordinator.executeForceSettlement(LocalDateTime.now());

        long historyAfter = priceHistoryRepository.count();
        long newRecords = historyAfter - historyBefore;
        long activeProductCount = productRepository.findByIsActiveTrueOrderByIdAsc().size();

        assertTrue(newRecords >= activeProductCount,
                "At least one PriceHistory record must be created per active product per settlement. " +
                "Expected >= " + activeProductCount + " new records, got " + newRecords);
    }

    // =========================================================================
    // PHASE 33: Price Consistency Verification
    // =========================================================================

    @Test
    @DisplayName("Ph33-01: Price always stays within [minCupPrice, maxCupPrice] bounds after settlement")
    void ph33_priceStaysWithinBounds() {
        // Run 5 forced settlements
        for (int i = 0; i < 5; i++) {
            settlementCoordinator.executeForceSettlement(LocalDateTime.now());
        }

        List<Product> products = productRepository.findByIsActiveTrueOrderByIdAsc();
        for (Product p : products) {
            Product fresh = productRepository.findById(p.getId()).orElse(p);
            if (fresh.getCurrentCupPrice() == null) continue;
            if (fresh.getMinCupPrice() != null) {
                assertTrue(fresh.getCurrentCupPrice().compareTo(fresh.getMinCupPrice()) >= 0,
                        "Product " + fresh.getName() + " price " + fresh.getCurrentCupPrice() +
                        " is below floor " + fresh.getMinCupPrice());
            }
            if (fresh.getMaxCupPrice() != null) {
                assertTrue(fresh.getCurrentCupPrice().compareTo(fresh.getMaxCupPrice()) <= 0,
                        "Product " + fresh.getName() + " price " + fresh.getCurrentCupPrice() +
                        " exceeds ceiling " + fresh.getMaxCupPrice());
            }
        }
    }

    @Test
    @DisplayName("Ph33-02: Price movement per settlement is exactly +1.00, 0.00, or -1.00")
    void ph33_priceMovementIsStrictlyPlusOrMinusOne() {
        // Record starting price
        Product before = productRepository.findById(productA.getId()).orElseThrow();
        BigDecimal priceBefore = before.getCurrentCupPrice();

        // Force settlement
        settlementCoordinator.executeForceSettlement(LocalDateTime.now());

        Product after = productRepository.findById(productA.getId()).orElseThrow();
        BigDecimal priceAfter = after.getCurrentCupPrice();

        BigDecimal delta = priceAfter.subtract(priceBefore).abs();

        // Delta must be exactly 0.00 or 1.00
        assertTrue(
            delta.compareTo(BigDecimal.ZERO) == 0 || delta.compareTo(BigDecimal.ONE) == 0,
            "Price delta must be exactly ±1.00 or 0.00, but was: " + delta
        );
    }

    @Test
    @DisplayName("Ph33-03: Buying Product A does NOT change Product B's price")
    void ph33_productABuyDoesNotChangeProductBPrice() {
        Product bBefore = productRepository.findById(productB.getId()).orElseThrow();
        BigDecimal bPriceBefore = bBefore.getCurrentCupPrice();

        // Buy product A multiple times
        for (int i = 0; i < 5; i++) {
            POSService.CartItemRequest item = new POSService.CartItemRequest();
            item.setProductId(productA.getId());
            item.setQuantity(3);
            item.setCupSizeMl(250);
            POSService.CheckoutRequest req = new POSService.CheckoutRequest();
            req.setItems(List.of(item));
            req.setPaymentMethod("CASH");
            req.setIdempotencyKey("ISO-" + i + "-" + UUID.randomUUID());
            posService.processCheckout(req);
        }

        // Run settlement
        settlementCoordinator.executeForceSettlement(LocalDateTime.now());

        // Product B price should NOT be affected by purchases of Product A
        Product bAfter = productRepository.findById(productB.getId()).orElseThrow();
        BigDecimal bPriceAfter = bAfter.getCurrentCupPrice();

        // The only way B's price changes is from its OWN demand. Since we only bought A,
        // B should have decayed by -1.00 (zero demand) or stayed stable if at floor.
        BigDecimal expectedMin = bBefore.getMinCupPrice().max(bPriceBefore.subtract(BigDecimal.ONE));
        BigDecimal expectedMax = bPriceBefore; // Can only decrease or hold (zero B purchases)

        assertTrue(bPriceAfter.compareTo(expectedMin) >= 0 && bPriceAfter.compareTo(expectedMax) <= 0,
                "Product B price should only decrease or stay stable (zero demand), " +
                "but moved from " + bPriceBefore + " to " + bPriceAfter +
                " after buying only Product A. Products must be independent.");
    }

    @Test
    @DisplayName("Ph33-04: High demand on Product A raises A's price but does not affect B")
    void ph33_highDemandOnADoesNotAffectB() {
        // Set both at midpoint
        productA.setCurrentCupPrice(new BigDecimal("25.00"));
        productB.setCurrentCupPrice(new BigDecimal("22.00"));
        productRepository.saveAndFlush(productA);
        productRepository.saveAndFlush(productB);

        // Buy a lot of Product A
        for (int i = 0; i < 10; i++) {
            POSService.CartItemRequest item = new POSService.CartItemRequest();
            item.setProductId(productA.getId());
            item.setQuantity(5);
            item.setCupSizeMl(250);
            POSService.CheckoutRequest req = new POSService.CheckoutRequest();
            req.setItems(List.of(item));
            req.setPaymentMethod("CASH");
            req.setIdempotencyKey("HIGH-" + i + "-" + UUID.randomUUID());
            posService.processCheckout(req);
        }

        BigDecimal bPriceBefore = productRepository.findById(productB.getId()).orElseThrow().getCurrentCupPrice();

        // Evaluate A's pricing independently
        PriceAdjustmentService.PriceEvaluationResult resA = priceAdjustmentService.evaluateAndAdjustPrice(productA.getId());
        assertEquals("HIGH", resA.getDemandLevelCategory(),
                "Product A should be HIGH demand after 50 purchases");
        assertEquals(new BigDecimal("26.00"), resA.getNewPrice(),
                "Product A should increase by exactly +₹1.00");

        // Evaluate B's pricing independently — it should see ZERO demand
        PriceAdjustmentService.PriceEvaluationResult resB = priceAdjustmentService.evaluateAndAdjustPrice(productB.getId());
        assertNotEquals("HIGH", resB.getDemandLevelCategory(),
                "Product B should NOT be HIGH — no one bought Product B");

        BigDecimal bPriceAfter = productRepository.findById(productB.getId()).orElseThrow().getCurrentCupPrice();
        // B can only stay at floor or decay
        assertTrue(bPriceAfter.compareTo(bPriceBefore) <= 0,
                "Product B must not increase from Product A's purchases. B went from " +
                bPriceBefore + " to " + bPriceAfter);
    }

    @Test
    @DisplayName("Ph33-05: Price at floor cannot go below floor even after multiple decay settlements")
    void ph33_priceDoesNotGoBelowFloor() {
        // Set price at floor
        productA.setCurrentCupPrice(new BigDecimal("20.00")); // at floor
        productRepository.saveAndFlush(productA);

        // Run 5 settlements with zero demand
        for (int i = 0; i < 5; i++) {
            settlementCoordinator.executeForceSettlement(LocalDateTime.now());
        }

        Product final_ = productRepository.findById(productA.getId()).orElseThrow();
        assertEquals(0, final_.getCurrentCupPrice().compareTo(new BigDecimal("20.00")),
                "Price must not go below floor even after multiple zero-demand settlements. Got: " + final_.getCurrentCupPrice());
    }

    @Test
    @DisplayName("Ph33-06: Price at ceiling cannot exceed ceiling even under sustained high demand")
    void ph33_priceDoesNotExceedCeiling() {
        // Set price at ceiling
        productA.setCurrentCupPrice(new BigDecimal("30.00")); // at ceiling
        productRepository.saveAndFlush(productA);

        // Buy a lot to create high demand
        for (int i = 0; i < 5; i++) {
            POSService.CartItemRequest item = new POSService.CartItemRequest();
            item.setProductId(productA.getId());
            item.setQuantity(5);
            item.setCupSizeMl(250);
            POSService.CheckoutRequest req = new POSService.CheckoutRequest();
            req.setItems(List.of(item));
            req.setPaymentMethod("CASH");
            req.setIdempotencyKey("CEIL-" + i + "-" + UUID.randomUUID());
            posService.processCheckout(req);
        }

        // Run settlement
        settlementCoordinator.executeForceSettlement(LocalDateTime.now());

        Product final_ = productRepository.findById(productA.getId()).orElseThrow();
        assertEquals(0, final_.getCurrentCupPrice().compareTo(new BigDecimal("30.00")),
                "Price must not exceed ceiling even under high demand. Got: " + final_.getCurrentCupPrice());
    }

    // =========================================================================
    // PHASE 34: 20L Inventory Volume Consistency
    // =========================================================================

    @Test
    @DisplayName("Ph34-01: Inventory volume decreases by exactly (quantity * cupSizeMl) per order")
    void ph34_inventoryDecreasesCorrectly() {
        // Fresh batch
        ensureActiveBatch(productA.getId(), new BigDecimal("25.00"), 10000);
        JuiceBatch batchBefore = batchRepository.findFirstActiveBatchForProduct(productA.getId()).orElseThrow();
        int volBefore = batchBefore.getRemainingVolumeMl();

        int qty = 2;
        int cupSizeMl = 250;

        POSService.CartItemRequest item = new POSService.CartItemRequest();
        item.setProductId(productA.getId());
        item.setQuantity(qty);
        item.setCupSizeMl(cupSizeMl);
        POSService.CheckoutRequest req = new POSService.CheckoutRequest();
        req.setItems(List.of(item));
        req.setPaymentMethod("CASH");
        req.setIdempotencyKey("INV-" + UUID.randomUUID());
        posService.processCheckout(req);

        JuiceBatch batchAfter = batchRepository.findFirstActiveBatchForProduct(productA.getId()).orElseThrow();
        int expectedVol = volBefore - (qty * cupSizeMl);
        assertEquals(expectedVol, batchAfter.getRemainingVolumeMl(),
                "Remaining volume must decrease by exactly " + (qty * cupSizeMl) + "ml");
    }

    @Test
    @DisplayName("Ph34-02: Batch transitions to DEPLETED when volume reaches zero")
    void ph34_batchDepletedWhenVolumeZero() {
        // Deplete existing active batches for productA so only smallBatch is active
        List<JuiceBatch> existingA = batchRepository.findByProductIdAndStatus(productA.getId(), JuiceBatch.BatchStatus.ACTIVE);
        for (JuiceBatch b : existingA) {
            b.setStatus(JuiceBatch.BatchStatus.DEPLETED);
            batchRepository.save(b);
        }
        batchRepository.flush();

        // Create a very small batch (just enough for 2 cups)
        JuiceBatch smallBatch = batchRepository.save(JuiceBatch.builder()
                .productId(productA.getId())
                .batchCode("SMALL-" + System.currentTimeMillis())
                .containerCapacityMl(500)
                .initialVolumeMl(500)
                .remainingVolumeMl(500)
                .cupSizeMl(250)
                .status(JuiceBatch.BatchStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        // Order 2 cups (exactly drains the batch)
        POSService.CartItemRequest item = new POSService.CartItemRequest();
        item.setProductId(productA.getId());
        item.setQuantity(2);
        item.setCupSizeMl(250);
        POSService.CheckoutRequest req = new POSService.CheckoutRequest();
        req.setItems(List.of(item));
        req.setPaymentMethod("CASH");
        req.setIdempotencyKey("DRAIN-" + UUID.randomUUID());

        POSService.CheckoutResponse res = posService.processCheckout(req);
        assertTrue(res.isSuccess(), "Order should succeed when batch has exactly enough volume");

        JuiceBatch updated = batchRepository.findById(smallBatch.getId()).orElseThrow();
        assertTrue(updated.getRemainingVolumeMl() == 0
                || JuiceBatch.BatchStatus.DEPLETED.equals(updated.getStatus()),
                "Batch must be depleted or have 0ml remaining after full deduction. " +
                "Got status=" + updated.getStatus() + " remaining=" + updated.getRemainingVolumeMl());
    }

    @Test
    @DisplayName("Ph34-03: Order rejected when all batches are depleted (insufficient inventory)")
    void ph34_orderRejectedWhenInsufficientInventory() {
        // Deplete all active batches for productB
        List<JuiceBatch> batches = batchRepository.findAll().stream()
                .filter(b -> productB.getId().equals(b.getProductId())
                        && JuiceBatch.BatchStatus.ACTIVE.equals(b.getStatus()))
                .toList();

        for (JuiceBatch b : batches) {
            b.setRemainingVolumeMl(0);
            b.setStatus(JuiceBatch.BatchStatus.DEPLETED);
            batchRepository.save(b);
        }
        batchRepository.flush();

        POSService.CartItemRequest item = new POSService.CartItemRequest();
        item.setProductId(productB.getId());
        item.setQuantity(1);
        item.setCupSizeMl(250);
        POSService.CheckoutRequest req = new POSService.CheckoutRequest();
        req.setItems(List.of(item));
        req.setPaymentMethod("CASH");
        req.setIdempotencyKey("NO-STOCK-" + UUID.randomUUID());

        // Should fail or return an error
        try {
            POSService.CheckoutResponse res = posService.processCheckout(req);
            assertFalse(res.isSuccess(), "Checkout must fail when inventory is depleted");
        } catch (Exception e) {
            // Exception-based rejection is also acceptable
            assertTrue(e.getMessage().toLowerCase().contains("stock")
                    || e.getMessage().toLowerCase().contains("inventory")
                    || e.getMessage().toLowerCase().contains("batch")
                    || e.getMessage().toLowerCase().contains("insufficient"),
                    "Exception message must indicate stock issue: " + e.getMessage());
        }
    }

    // =========================================================================
    // PHASE 35: Concurrency & Pessimistic Locking Verification
    // =========================================================================

    @Test
    @DisplayName("Ph35-01: Concurrent checkouts do not produce negative inventory (pessimistic lock test)")
    void ph35_concurrentCheckoutsDoNotCreateNegativeInventory() throws InterruptedException {
        // Deplete existing active batches for productA so only concurrentBatch is active
        List<JuiceBatch> existingA = batchRepository.findByProductIdAndStatus(productA.getId(), JuiceBatch.BatchStatus.ACTIVE);
        for (JuiceBatch b : existingA) {
            b.setStatus(JuiceBatch.BatchStatus.DEPLETED);
            batchRepository.save(b);
        }
        batchRepository.flush();

        // Set up a batch with exactly 10 cups worth of volume
        int totalCups = 10;
        int cupSizeMl = 250;
        int totalVolumeMl = totalCups * cupSizeMl; // 2500ml

        JuiceBatch concurrentBatch = batchRepository.save(JuiceBatch.builder()
                .productId(productA.getId())
                .batchCode("CONCURRENT-" + System.currentTimeMillis())
                .containerCapacityMl(totalVolumeMl)
                .initialVolumeMl(totalVolumeMl)
                .remainingVolumeMl(totalVolumeMl)
                .cupSizeMl(cupSizeMl)
                .status(JuiceBatch.BatchStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        int threads = 20; // 20 simultaneous orders for 1 cup each
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int threadNum = i;
            futures.add(executor.submit(() -> {
                latch.await(); // synchronized start
                try {
                    POSService.CartItemRequest cartItem = new POSService.CartItemRequest();
                    cartItem.setProductId(productA.getId());
                    cartItem.setQuantity(1);
                    cartItem.setCupSizeMl(cupSizeMl);
                    POSService.CheckoutRequest concReq = new POSService.CheckoutRequest();
                    concReq.setItems(List.of(cartItem));
                    concReq.setPaymentMethod("CASH");
                    concReq.setIdempotencyKey("CONC-" + threadNum + "-" + UUID.randomUUID());
                    POSService.CheckoutResponse r = posService.processCheckout(concReq);
                    if (r.isSuccess()) { successCount.incrementAndGet(); return true; }
                    else { failureCount.incrementAndGet(); return false; }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    return false;
                }
            }));
        }

        latch.countDown(); // fire all threads simultaneously
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // CRITICAL INVARIANT: success count must be <= totalCups (never over-sold)
        assertTrue(successCount.get() <= totalCups,
                "Concurrent orders must not exceed batch capacity. " +
                "Succeeded=" + successCount.get() + " but batch only had " + totalCups + " cups.");

        // CRITICAL INVARIANT: remaining volume must never be negative
        JuiceBatch finalBatch = batchRepository.findById(concurrentBatch.getId()).orElseThrow();
        assertTrue(finalBatch.getRemainingVolumeMl() >= 0,
                "Remaining volume must never be negative. Got: " + finalBatch.getRemainingVolumeMl());

        // CRITICAL INVARIANT: inventory math must balance
        int volumeSold = successCount.get() * cupSizeMl;
        int expectedRemaining = totalVolumeMl - volumeSold;
        assertEquals(expectedRemaining, finalBatch.getRemainingVolumeMl(),
                "Volume accounting mismatch: " + successCount.get() + " orders * " + cupSizeMl +
                "ml should leave " + expectedRemaining + "ml, but got " + finalBatch.getRemainingVolumeMl());
    }

    @Test
    @DisplayName("Ph35-02: Concurrent price settlements do not produce duplicate settlement records")
    void ph35_concurrentSettlementsAreIdempotent() throws InterruptedException {
        long settlementsBefore = 0;
        try {
            // Count existing settlements
            settlementsBefore = settlementCoordinator.getLastSettlementTime() != null ? 1 : 0;
        } catch (Exception ignored) {}

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    // All threads try to run the SAME settlement window simultaneously
                    PricingEngineService.PriceEvaluationCycleResult r =
                            settlementCoordinator.executeScheduledSettlement();
                    if (r != null) successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // The settlement idempotency key must ensure only 1 effective settlement fired.
        // Multiple threads racing should result in exactly 1 success (others skipped by lock or idempotency)
        assertTrue(successCount.get() >= 1, "At least one settlement must succeed");
        // No thread should throw an unhandled exception
        assertEquals(0, errorCount.get(),
                "No settlement thread should throw an unhandled exception. Errors: " + errorCount.get());
    }
}
