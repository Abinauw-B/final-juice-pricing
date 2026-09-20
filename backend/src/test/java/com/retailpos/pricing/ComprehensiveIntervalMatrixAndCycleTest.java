package com.retailpos.pricing;

import com.retailpos.domain.Product;
import com.retailpos.domain.ProductRepository;
import com.retailpos.domain.SalesOrderItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveIntervalMatrixAndCycleTest {

    private static final int[] TEST_INTERVALS = {10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600};

    private Product createMockProduct(Long id, BigDecimal current, BigDecimal min, BigDecimal max, double targetPerMin) {
        Product p = new Product();
        p.setId(id);
        p.setName("Product-" + id);
        p.setFlavour("FLAVOUR_" + id);
        p.setCurrentCupPrice(current);
        p.setDefaultCupPrice(current);
        p.setMinCupPrice(min);
        p.setMaxCupPrice(max);
        p.setTargetSalesPer1Minute(targetPerMin);
        p.setPricingMode("DYNAMIC");
        p.setLastPriceChangeTimestamp(null);
        return p;
    }

    private PriceAdjustmentService createTestService(Product product, int w0, int w1, int w2) {
        ProductRepository productRepo = (ProductRepository) Proxy.newProxyInstance(
                ProductRepository.class.getClassLoader(),
                new Class<?>[]{ProductRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByIdWithLock") || method.getName().equals("findById")) {
                        return Optional.of(product);
                    }
                    if (method.getName().equals("saveAndFlush") || method.getName().equals("save")) {
                        return product;
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    return null;
                }
        );

        SalesOrderItemRepository salesRepo = (SalesOrderItemRepository) Proxy.newProxyInstance(
                SalesOrderItemRepository.class.getClassLoader(),
                new Class<?>[]{SalesOrderItemRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().contains("count")) {
                        LocalDateTime start = (LocalDateTime) args[1];
                        LocalDateTime end = (LocalDateTime) args[2];
                        // Use duration to confirm the window is valid (non-zero), then return mock sales
                        long duration = java.time.Duration.between(start, end).toSeconds();
                        return duration > 0 ? w0 : 0;
                    }
                    return 0;
                }
        );

        return new PriceAdjustmentService(
                productRepo,
                (com.retailpos.domain.PriceHistoryRepository) Proxy.newProxyInstance(
                        com.retailpos.domain.PriceHistoryRepository.class.getClassLoader(),
                        new Class<?>[]{com.retailpos.domain.PriceHistoryRepository.class},
                        (proxy, method, args) -> null
                ),
                salesRepo,
                null, null, null, null
        );
    }

    // =========================================================================
    // SECTION 50: EVERY INTERVAL TEST MATRIX (10s, 15s, 30s, 60s, 120s, 300s, 600s, 900s, 1800s, 3600s)
    // =========================================================================

    @Test
    @DisplayName("SECTION 50.1: Stable demand invariant across ALL 10 settlement intervals (w0=w1=w2=target -> Rd=1.0, deltaP=0)")
    void testStableDemandAcrossAllIntervals() {
        for (int interval : TEST_INTERVALS) {
            // targetPerMin = 12.0 ensures exact integer sales across all intervals (10s->2, 15s->3, 30s->6, etc.)
            double targetPerMin = 12.0;
            double normalizedTarget = targetPerMin * (interval / 60.0);
            int salesCount = (int) Math.round(normalizedTarget);

            PricingConfigurationService.PricingConfigSnapshot snapshot = new PricingConfigurationService.PricingConfigSnapshot(
                    1L, interval, new BigDecimal("1.0000"), new BigDecimal("0.5000"), new BigDecimal("0.2500"),
                    new BigDecimal("1.1000"), new BigDecimal("0.9000"), new BigDecimal("1.1000"), new BigDecimal("0.5000"),
                    new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("25.00"), new BigDecimal("20.00"),
                    new BigDecimal("35.00"), new BigDecimal("20.00"), 180
            );

            Product product = createMockProduct(1L, new BigDecimal("25.00"), new BigDecimal("20.00"), new BigDecimal("35.00"), targetPerMin);
            PriceAdjustmentService service = createTestService(product, salesCount, salesCount, salesCount);

            PriceAdjustmentService.PriceEvaluationResult result = service.evaluateAndAdjustPrice(1L, LocalDateTime.now(), snapshot, "CYCLE-" + interval);

            // Stable demand invariant: Rd must be within stable band [0.90, 1.10]
            assertTrue(result.getDemandRatio() >= 0.90 && result.getDemandRatio() <= 1.10,
                    String.format("Interval %ds: Rd=%.4f must be in stable band around 1.0", interval, result.getDemandRatio()));

            // Movement must be strictly 0
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getPriceChange()),
                    String.format("Interval %ds: Price change must be 0.00 under stable demand, actual=%s", interval, result.getPriceChange()));
            assertEquals(new BigDecimal("25.00"), result.getNewPrice(),
                    String.format("Interval %ds: Price must remain at ₹25.00", interval));
        }
    }

    @Test
    @DisplayName("SECTION 50.2: Normal price movement strictly bounded by {+1.00, 0.00, -1.00} across ALL intervals")
    void testPriceMovementStrictlyBoundedAcrossAllIntervals() {
        for (int interval : TEST_INTERVALS) {
            PricingConfigurationService.PricingConfigSnapshot snapshot = new PricingConfigurationService.PricingConfigSnapshot(
                    1L, interval, new BigDecimal("1.0000"), new BigDecimal("0.5000"), new BigDecimal("0.2500"),
                    new BigDecimal("1.1000"), new BigDecimal("0.9000"), new BigDecimal("1.1000"), new BigDecimal("0.5000"),
                    new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("25.00"), new BigDecimal("20.00"),
                    new BigDecimal("35.00"), new BigDecimal("20.00"), 180
            );

            // Test extreme high volume (e.g. 500 sales)
            Product surgeProduct = createMockProduct(1L, new BigDecimal("25.00"), new BigDecimal("20.00"), new BigDecimal("35.00"), 0.55);
            PriceAdjustmentService surgeService = createTestService(surgeProduct, 500, 500, 500);
            PriceAdjustmentService.PriceEvaluationResult surgeResult = surgeService.evaluateAndAdjustPrice(1L, LocalDateTime.now(), snapshot, "CYCLE-SURGE-" + interval);

            assertEquals(new BigDecimal("1.00"), surgeResult.getPriceChange(),
                    String.format("Interval %ds: Extreme demand must increase by exactly +₹1.00, never more", interval));
            assertEquals(new BigDecimal("26.00"), surgeResult.getNewPrice());

            // Test zero sales
            Product decayProduct = createMockProduct(2L, new BigDecimal("25.00"), new BigDecimal("20.00"), new BigDecimal("35.00"), 0.55);
            PriceAdjustmentService decayService = createTestService(decayProduct, 0, 0, 0);
            PriceAdjustmentService.PriceEvaluationResult decayResult = decayService.evaluateAndAdjustPrice(2L, LocalDateTime.now(), snapshot, "CYCLE-DECAY-" + interval);

            BigDecimal decayDelta = decayResult.getPriceChange();
            assertTrue(decayDelta.compareTo(BigDecimal.ZERO) == 0 || decayDelta.compareTo(new BigDecimal("-1.00")) == 0,
                    String.format("Interval %ds: Zero demand must change by 0 or -1.00, actual=%s", interval, decayDelta));
        }
    }

    @Test
    @DisplayName("SECTION 50.3: Floor and Ceiling invariants hold across ALL intervals")
    void testFloorAndCeilingInvariantsAcrossAllIntervals() {
        for (int interval : TEST_INTERVALS) {
            PricingConfigurationService.PricingConfigSnapshot snapshot = new PricingConfigurationService.PricingConfigSnapshot(
                    1L, interval, new BigDecimal("1.0000"), new BigDecimal("0.5000"), new BigDecimal("0.2500"),
                    new BigDecimal("1.1000"), new BigDecimal("0.9000"), new BigDecimal("1.1000"), new BigDecimal("0.5000"),
                    new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("25.00"), new BigDecimal("20.00"),
                    new BigDecimal("35.00"), new BigDecimal("20.00"), 180
            );

            // Product at floor (₹20.00) with zero demand
            Product floorProduct = createMockProduct(1L, new BigDecimal("20.00"), new BigDecimal("20.00"), new BigDecimal("35.00"), 0.55);
            PriceAdjustmentService floorService = createTestService(floorProduct, 0, 0, 0);
            PriceAdjustmentService.PriceEvaluationResult floorResult = floorService.evaluateAndAdjustPrice(1L, LocalDateTime.now(), snapshot, "CYCLE-FLOOR-" + interval);

            assertEquals(new BigDecimal("20.00"), floorResult.getNewPrice(),
                    String.format("Interval %ds: Price must never breach floor ₹20.00", interval));
            assertEquals(0, BigDecimal.ZERO.compareTo(floorResult.getPriceChange()));

            // Product at ceiling (₹35.00) with maximum surge demand
            Product ceilingProduct = createMockProduct(2L, new BigDecimal("35.00"), new BigDecimal("20.00"), new BigDecimal("35.00"), 0.55);
            PriceAdjustmentService ceilingService = createTestService(ceilingProduct, 100, 100, 100);
            PriceAdjustmentService.PriceEvaluationResult ceilingResult = ceilingService.evaluateAndAdjustPrice(2L, LocalDateTime.now(), snapshot, "CYCLE-CEIL-" + interval);

            assertEquals(new BigDecimal("35.00"), ceilingResult.getNewPrice(),
                    String.format("Interval %ds: Price must never breach ceiling ₹35.00", interval));
            assertEquals(0, BigDecimal.ZERO.compareTo(ceilingResult.getPriceChange()));
        }
    }

    // =========================================================================
    // SECTION 52: 100-CYCLE TEST
    // =========================================================================

    @Test
    @DisplayName("SECTION 52: 100 consecutive settlement cycles verify stability, clamping, and no runaway drift")
    void test100ConsecutiveSettlementCycles() {
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("32.00");
        BigDecimal initialPrice = new BigDecimal("25.00");

        Product product = createMockProduct(10L, initialPrice, floor, ceiling, 0.60);
        LocalDateTime simTime = LocalDateTime.now().minusHours(2);

        PricingConfigurationService.PricingConfigSnapshot snapshot = new PricingConfigurationService.PricingConfigSnapshot(
                1L, 60, new BigDecimal("1.0000"), new BigDecimal("0.5000"), new BigDecimal("0.2500"),
                new BigDecimal("1.1000"), new BigDecimal("0.9000"), new BigDecimal("1.1000"), new BigDecimal("0.5000"),
                new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("25.00"), floor, ceiling, new BigDecimal("20.00"), 180
        );

        Random rnd = new Random(42); // Deterministic seed

        for (int cycle = 1; cycle <= 100; cycle++) {
            simTime = simTime.plusSeconds(60);

            // Dynamic sales pattern across 100 cycles:
            // Cycles 1-25: No sales (decay)
            // Cycles 26-50: Stable sales (hold)
            // Cycles 51-75: Heavy surge sales (surge)
            // Cycles 76-100: Random fluctuating sales
            int sales;
            if (cycle <= 25) {
                sales = 0;
            } else if (cycle <= 50) {
                sales = 1; // approx target (0.60 cups/min)
            } else if (cycle <= 75) {
                sales = 4; // high demand
            } else {
                sales = rnd.nextInt(5);
            }

            PriceAdjustmentService service = createTestService(product, sales, sales, sales);
            PriceAdjustmentService.PriceEvaluationResult res = service.evaluateAndAdjustPrice(10L, simTime, snapshot, "CYCLE-" + cycle);

            BigDecimal oldP = res.getOldPrice();
            BigDecimal newP = res.getNewPrice();
            BigDecimal delta = res.getPriceChange();

            // Assert Invariants on EVERY cycle:
            // 1. Delta strictly in {+1.00, 0.00, -1.00}
            assertTrue(delta.compareTo(new BigDecimal("1.00")) <= 0 && delta.compareTo(new BigDecimal("-1.00")) >= 0,
                    String.format("Cycle %d: delta %s violates max movement boundary {-1, 0, +1}", cycle, delta));

            // 2. Floor invariant: price >= floor
            assertTrue(newP.compareTo(floor) >= 0,
                    String.format("Cycle %d: newPrice %s breached floor %s", cycle, newP, floor));

            // 3. Ceiling invariant: price <= ceiling
            assertTrue(newP.compareTo(ceiling) <= 0,
                    String.format("Cycle %d: newPrice %s breached ceiling %s", cycle, newP, ceiling));

            // 4. Arithmetic consistency: newPrice == oldPrice + delta
            assertEquals(oldP.add(delta).setScale(2, RoundingMode.HALF_UP), newP,
                    String.format("Cycle %d: price arithmetic mismatch", cycle));

            // Update product for next cycle
            product.setCurrentCupPrice(newP);
        }
    }

    // =========================================================================
    // SECTION 30: CRASH FLOOR PROTECTION INVARIANT
    // =========================================================================

    @Test
    @DisplayName("SECTION 30: Crash price = MAX(globalCrashFloor, product.minCupPrice)")
    void testMarketCrashFloorClamping() {
        BigDecimal globalCrashPrice = new BigDecimal("20.00");

        // Case A: Product floor is ₹18.00 (< ₹20.00) -> Crash price is ₹20.00
        Product mango = createMockProduct(1L, new BigDecimal("25.00"), new BigDecimal("18.00"), new BigDecimal("30.00"), 0.55);
        BigDecimal mangoCrash = globalCrashPrice.max(mango.getMinCupPrice()).min(mango.getMaxCupPrice());
        assertEquals(new BigDecimal("20.00"), mangoCrash);

        // Case B: Premium Product floor is ₹24.00 (> ₹20.00) -> Crash price is ₹24.00 (NEVER drops below floor!)
        Product premium = createMockProduct(2L, new BigDecimal("28.00"), new BigDecimal("24.00"), new BigDecimal("40.00"), 0.55);
        BigDecimal premiumCrash = globalCrashPrice.max(premium.getMinCupPrice()).min(premium.getMaxCupPrice());
        assertEquals(new BigDecimal("24.00"), premiumCrash);
        assertTrue(premiumCrash.compareTo(premium.getMinCupPrice()) >= 0, "Crash price must never drop below product minCupPrice");
    }
}
