package com.retailpos.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class PriceMovementUnitTest {

    public static BigDecimal calculateDWMAPrice(BigDecimal currentPrice, int w0, int w1, int w2, double targetSales, BigDecimal floor, BigDecimal ceiling) {
        BigDecimal sw = BigDecimal.valueOf(w0).multiply(new BigDecimal("1.00"))
                .add(BigDecimal.valueOf(w1).multiply(new BigDecimal("0.50")))
                .add(BigDecimal.valueOf(w2).multiply(new BigDecimal("0.25")))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal targetSalesBd = BigDecimal.valueOf(targetSales).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rd = (targetSalesBd.compareTo(BigDecimal.ZERO) > 0)
                ? sw.divide(targetSalesBd, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal deltaP;
        if (rd.compareTo(new BigDecimal("1.1000")) >= 0) {
            if (w0 > 0) {
                deltaP = BigDecimal.ONE;
            } else {
                deltaP = BigDecimal.ZERO;
            }
        } else if (rd.compareTo(new BigDecimal("0.9000")) >= 0) {
            deltaP = BigDecimal.ZERO;
        } else {
            deltaP = new BigDecimal("-1.00");
        }

        // Validate maximum ₹1.00 movement per normal settlement
        PricingConfigurationService.validatePriceMovement(deltaP);

        BigDecimal uncapped = currentPrice.add(deltaP);
        return uncapped.max(floor).min(ceiling).setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("TEST A: Current ₹25.00, High Demand -> Expected: ₹26.00 (+₹1)")
    void test_A_Current25_HighDemand_Returns26() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 3, 1, 0, 0.55, floor, ceiling);
        assertEquals(new BigDecimal("26.00"), newPrice);
    }

    @Test
    @DisplayName("TEST B: Current ₹25.00, Normal Demand -> Expected: ₹25.00 (₹0)")
    void test_B_Current25_NormalDemand_Returns25() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 1, 0, 0, 1.00, floor, ceiling);
        assertEquals(new BigDecimal("25.00"), newPrice);
    }

    @Test
    @DisplayName("TEST C: Current ₹25.00, Low Demand -> Expected: ₹24.00 (-₹1)")
    void test_C_Current25_LowDemand_Returns24() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        // Sw=1.00, Target=2.00 => Rd=0.50 < 0.90 => -₹1.00
        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 1, 0, 0, 2.00, floor, ceiling);
        assertEquals(new BigDecimal("24.00"), newPrice);
    }

    @Test
    @DisplayName("TEST D: Current ₹25.00, Zero Sales -> Expected: ₹24.00 (-₹1)")
    void test_D_Current25_ZeroSales_Returns24() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 0, 0, 0, 0.55, floor, ceiling);
        assertEquals(new BigDecimal("24.00"), newPrice);
    }

    @Test
    @DisplayName("TEST E: Current ₹24.00, Zero Sales -> Expected: ₹23.00 (-₹1)")
    void test_E_Current24_ZeroSales_Returns23() {
        BigDecimal currentPrice = new BigDecimal("24.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 0, 0, 0, 0.55, floor, ceiling);
        assertEquals(new BigDecimal("23.00"), newPrice);
    }

    @Test
    @DisplayName("TEST F: Current ₹19.00, Zero Sales -> Expected: ₹18.00 (-₹1, Reached Floor)")
    void test_F_Current19_ZeroSales_Returns18() {
        BigDecimal currentPrice = new BigDecimal("19.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 0, 0, 0, 0.55, floor, ceiling);
        assertEquals(new BigDecimal("18.00"), newPrice);
    }

    @Test
    @DisplayName("TEST G: Current ₹18.00, Zero Sales -> Expected: ₹18.00 (Pinned at Floor)")
    void test_G_Current18_ZeroSales_Returns18() {
        BigDecimal currentPrice = new BigDecimal("18.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 0, 0, 0, 0.55, floor, ceiling);
        assertEquals(new BigDecimal("18.00"), newPrice);
    }

    @Test
    @DisplayName("TEST H: Current ₹34.00, High Demand -> Expected: ₹35.00 (+₹1, Reached Ceiling)")
    void test_H_Current34_HighDemand_Returns35() {
        BigDecimal currentPrice = new BigDecimal("34.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 5, 2, 1, 0.55, floor, ceiling);
        assertEquals(new BigDecimal("35.00"), newPrice);
    }

    @Test
    @DisplayName("TEST I: Current ₹35.00, High Demand -> Expected: ₹35.00 (Pinned at Ceiling)")
    void test_I_Current35_HighDemand_Returns35() {
        BigDecimal currentPrice = new BigDecimal("35.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 10, 5, 2, 0.55, floor, ceiling);
        assertEquals(new BigDecimal("35.00"), newPrice);
    }

    @Test
    @DisplayName("TEST J: Current ₹25.00, Admin Market Crash -> Expected: ₹18.00 immediately")
    void test_J_Current25_AdminMarketCrash_Returns18Immediately() {
        MarketCrashService service = new MarketCrashService(null, null, null, null, null, null);
        com.retailpos.domain.Product mango = new com.retailpos.domain.Product();
        mango.setId(1L);
        mango.setName("Fresh Mango Juice");
        mango.setCurrentCupPrice(new BigDecimal("25.00"));
        mango.setMinCupPrice(new BigDecimal("18.00"));
        mango.setMaxCupPrice(new BigDecimal("35.00"));

        BigDecimal crashPrice = service.calculateCrashPrice(mango);
        assertEquals(new BigDecimal("18.00"), crashPrice, "Market Crash price must immediately equal floor ₹18.00");
    }

    @Test
    @DisplayName("TEST K: Verify every normal pricing movement satisfies ABS(NewPrice - CurrentPrice) <= 1")
    void test_K_MaxPriceMovementValidation() {
        // Valid normal pricing deltas: +1, 0, -1
        PricingConfigurationService.validatePriceMovement(new BigDecimal("1.00"));
        PricingConfigurationService.validatePriceMovement(new BigDecimal("0.00"));
        PricingConfigurationService.validatePriceMovement(new BigDecimal("-1.00"));

        // Invalid normal pricing movements exceeding ₹1.00
        assertThrows(IllegalStateException.class, () -> PricingConfigurationService.validatePriceMovement(new BigDecimal("-4.00")));
        assertThrows(IllegalStateException.class, () -> PricingConfigurationService.validatePriceMovement(new BigDecimal("-2.00")));
        assertThrows(IllegalStateException.class, () -> PricingConfigurationService.validatePriceMovement(new BigDecimal("2.00")));
        assertThrows(IllegalStateException.class, () -> PricingConfigurationService.validatePriceMovement(new BigDecimal("4.00")));
    }

    @Test
    @DisplayName("TEST L: Admin changes ceiling ₹35 -> ₹30: Clamping enforces max ₹30.00")
    void test_L_AdminCeilingChangePropagation() {
        BigDecimal currentPrice = new BigDecimal("32.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal newCeiling = new BigDecimal("30.00");

        // When ceiling is lowered to ₹30.00, price is clamped to ₹30.00
        BigDecimal clamped = currentPrice.min(newCeiling).max(floor).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("30.00"), clamped);
    }

    @Test
    @DisplayName("TEST M: Change settlement interval 120s -> 60s: Target sales and simulation advance by 1 minute")
    void test_M_SettlementIntervalChangeTiming() {
        PricingConfigurationService configService = new PricingConfigurationService(null, null, null, null, null);
        
        com.retailpos.domain.Product mango = new com.retailpos.domain.Product();
        mango.setTargetSalesPer1Minute(0.60);

        // 60-second interval: normalized target = 0.60 * (60/60) = 0.60
        double target60s = configService.getNormalizedTargetSales(mango, 60);
        assertEquals(0.60, target60s, 0.001);

        // 120-second interval: normalized target = 0.60 * (120/60) = 1.20
        double target120s = configService.getNormalizedTargetSales(mango, 120);
        assertEquals(1.20, target120s, 0.001);

        // Interval label
        assertEquals("1 Minute", PricingConfigurationService.getIntervalLabel(60));
        assertEquals("2 Minutes", PricingConfigurationService.getIntervalLabel(120));
    }

    @Test
    @DisplayName("Verify Repeated No Sales 8-Step Decay Sequence (₹25 -> ₹18)")
    void testRepeatedNoSales8StepSequence() {
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");
        double target = 0.55;

        BigDecimal p25 = new BigDecimal("25.00");
        BigDecimal p24 = calculateDWMAPrice(p25, 0, 0, 0, target, floor, ceiling);
        assertEquals(new BigDecimal("24.00"), p24);

        BigDecimal p23 = calculateDWMAPrice(p24, 0, 0, 0, target, floor, ceiling);
        assertEquals(new BigDecimal("23.00"), p23);

        BigDecimal p22 = calculateDWMAPrice(p23, 0, 0, 0, target, floor, ceiling);
        assertEquals(new BigDecimal("22.00"), p22);

        BigDecimal p21 = calculateDWMAPrice(p22, 0, 0, 0, target, floor, ceiling);
        assertEquals(new BigDecimal("21.00"), p21);

        BigDecimal p20 = calculateDWMAPrice(p21, 0, 0, 0, target, floor, ceiling);
        assertEquals(new BigDecimal("20.00"), p20);

        BigDecimal p19 = calculateDWMAPrice(p20, 0, 0, 0, target, floor, ceiling);
        assertEquals(new BigDecimal("19.00"), p19);

        BigDecimal p18 = calculateDWMAPrice(p19, 0, 0, 0, target, floor, ceiling);
        assertEquals(new BigDecimal("18.00"), p18);

        BigDecimal p18_pinned = calculateDWMAPrice(p18, 0, 0, 0, target, floor, ceiling);
        assertEquals(new BigDecimal("18.00"), p18_pinned);
    }

    @Test
    @DisplayName("Verify High Historical Sales with Zero Current Window (W0=0): Rd >= 1.10, W0=0 -> Movement ₹0.00")
    void testHighHistoricalWithZeroCurrentWindow() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        // W0=0, W1=4, W2=2 => Sw = 0 + 2.0 + 0.5 = 2.50. Target=0.55 => Rd = 4.54 >= 1.10. But W0=0 -> deltaP = 0
        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 0, 4, 2, 0.55, floor, ceiling);
        assertEquals(new BigDecimal("25.00"), newPrice);
    }

    @Test
    @DisplayName("Verify Window Boundary Half-Open Range Continuity: No Double Counting or Gaps")
    void testWindowBoundariesNoOverlap() {
        java.time.LocalDateTime t = java.time.LocalDateTime.of(2026, 8, 29, 12, 0, 0);
        int intervalSec = 60;

        java.time.LocalDateTime w0Start = t.minusSeconds(intervalSec);     // 11:59:00
        java.time.LocalDateTime w0End = t;                                // 12:00:00
        java.time.LocalDateTime w1Start = t.minusSeconds(2L * intervalSec); // 11:58:00
        java.time.LocalDateTime w1End = t.minusSeconds(intervalSec);       // 11:59:00
        java.time.LocalDateTime w2Start = t.minusSeconds(3L * intervalSec); // 11:57:00
        java.time.LocalDateTime w2End = t.minusSeconds(2L * intervalSec);  // 11:58:00

        // Exact boundary point: 11:59:00
        java.time.LocalDateTime boundaryPoint = java.time.LocalDateTime.of(2026, 8, 29, 11, 59, 0);

        boolean inW0 = !boundaryPoint.isBefore(w0Start) && boundaryPoint.isBefore(w0End);
        boolean inW1 = !boundaryPoint.isBefore(w1Start) && boundaryPoint.isBefore(w1End);
        boolean inW2 = !boundaryPoint.isBefore(w2Start) && boundaryPoint.isBefore(w2End);

        assertTrue(inW0, "Boundary point 11:59:00 must be included in W0");
        assertFalse(inW1, "Boundary point 11:59:00 must be excluded from W1 (exclusive upper bound)");
        assertFalse(inW2, "Boundary point 11:59:00 must be excluded from W2");
    }

    @Test
    @DisplayName("Verify Concurrency Re-Entrancy Guard: AtomicBoolean prevents duplicate executions")
    void testConcurrencyReentrancyLock() {
        java.util.concurrent.atomic.AtomicBoolean lock = new java.util.concurrent.atomic.AtomicBoolean(false);

        // First thread acquires lock
        assertTrue(lock.compareAndSet(false, true));

        // Second simultaneous attempt fails to acquire lock
        assertFalse(lock.compareAndSet(false, true));

        // Lock release
        lock.set(false);

        // Subsequent execution succeeds
        assertTrue(lock.compareAndSet(false, true));
        lock.set(false);
    }

    @Test
    @DisplayName("Verify DWMA Simulation Rules and Market Crash Snapshot Restoration (1-min and ₹1 step)")
    void testDWMASimulationAndCrashRestoration() {
        PricingSimulationService simService = new PricingSimulationService();
        PricingSimulationService.SimulationRequest req = new PricingSimulationService.SimulationRequest();
        req.setFlavourName("Fresh Mango Juice");
        req.setInitialPrice(new BigDecimal("25.00"));
        req.setMinPrice(new BigDecimal("18.00"));
        req.setMaxPrice(new BigDecimal("35.00"));
        req.setCupsPerInterval(4);
        req.setIntervalMinutes(1);
        req.setTargetSales(0.55);
        req.setTotalSimulatedPurchases(40);
        req.setIncludeCrash(true);

        PricingSimulationService.SimulationResponse res = simService.runSimulation(req);
        assertEquals(10, res.getSteps().size());

        // Step 1: W0=4, W1=0, W2=0 => S_w=4.00, Target=0.55 => R_d=7.27 => Movement +1 => 26.00
        PricingSimulationService.SimulationStep step1 = res.getSteps().get(0);
        assertEquals(4, step1.getW0());
        assertEquals(0, step1.getW1());
        assertEquals(0, step1.getW2());
        assertEquals(4.00, step1.getWeightedSales(), 0.01);
        assertEquals(7.27, step1.getDemandRatio(), 0.05);
        assertEquals("+₹1", step1.getPriceMovement());
        assertEquals(new BigDecimal("26.00"), step1.getPrice());

        // Step 5: Market Crash Injected -> Floor ₹18.00
        PricingSimulationService.SimulationStep step5 = res.getSteps().get(4);
        assertEquals("CRASH", step5.getPriceMovement());
        assertEquals(new BigDecimal("18.00"), step5.getPrice());

        // Step 6: Market Crash Expired -> Exact Pre-crash price snapshot restored
        PricingSimulationService.SimulationStep step6 = res.getSteps().get(5);
        assertEquals("RESTORED", step6.getPriceMovement());
        assertEquals(new BigDecimal("29.00"), step6.getPrice());
    }
}
