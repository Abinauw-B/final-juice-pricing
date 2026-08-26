package com.retailpos.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        } else if (rd.compareTo(new BigDecimal("0.5000")) >= 0) {
            deltaP = new BigDecimal("-1.00");
        } else {
            deltaP = new BigDecimal("-2.00");
        }

        BigDecimal uncapped = currentPrice.add(deltaP);
        return uncapped.max(floor).min(ceiling).setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("Example 1 — Mango High Demand: W0=3, W1=1, W2=0, Target=1.10 -> Sw=3.50, Rd=3.1818 -> +₹1.00 -> ₹26.00")
    void testExample1MangoHighDemand() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 3, 1, 0, 1.10, floor, ceiling);
        assertEquals(new BigDecimal("26.00"), newPrice);
    }

    @Test
    @DisplayName("Example 2 — Lemon Zero Demand Decay: W0=0, W1=0, W2=0, Target=0.80 -> Sw=0, Rd=0 -> -₹2.00 -> ₹23.00")
    void testExample2LemonZeroDemandDecay() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 0, 0, 0, 0.80, floor, ceiling);
        assertEquals(new BigDecimal("23.00"), newPrice);
    }

    @Test
    @DisplayName("Example 3 — Orange Stable Demand: W0=1, W1=0, W2=0, Target=1.10 -> Sw=1.00, Rd=0.9091 -> ₹0.00 -> ₹25.00")
    void testExample3OrangeStableDemand() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 1, 0, 0, 1.10, floor, ceiling);
        assertEquals(new BigDecimal("25.00"), newPrice);
    }

    @Test
    @DisplayName("Example 4 — Strawberry Floor Protection: Current=₹19.00, Zero Demand Decay -> Floor ₹18.00")
    void testExample4StrawberryFloorProtection() {
        BigDecimal currentPrice = new BigDecimal("19.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        // 19.00 - 2.00 = 17.00 -> clamped to 18.00
        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 0, 0, 0, 0.70, floor, ceiling);
        assertEquals(new BigDecimal("18.00"), newPrice);
    }

    @Test
    @DisplayName("Example 5 — Thunder Ceiling Protection: Current=₹34.50, High Demand -> Ceiling ₹35.00")
    void testExample5ThunderCeilingProtection() {
        BigDecimal currentPrice = new BigDecimal("34.50");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        // 34.50 + 1.00 = 35.50 -> clamped to 35.00
        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 6, 4, 2, 0.90, floor, ceiling);
        assertEquals(new BigDecimal("35.00"), newPrice);
    }

    @Test
    @DisplayName("Verify High Historical Sales with Zero Current Window (W0=0): Rd >= 1.10, W0=0 -> Movement ₹0.00")
    void testHighHistoricalWithZeroCurrentWindow() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        // W0=0, W1=4, W2=2 => Sw = 0 + 2.0 + 0.5 = 2.50. Target=1.00 => Rd = 2.50 >= 1.10. But W0=0 -> deltaP = 0
        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 0, 4, 2, 1.00, floor, ceiling);
        assertEquals(new BigDecimal("25.00"), newPrice);
    }

    @Test
    @DisplayName("Verify Below Normal Partial Decay (0.50 <= Rd < 0.90 -> -₹1.00)")
    void testBelowNormalPartialDecay() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        // W0=1, W1=0, W2=0 => Sw=1.00. Target=1.50 => Rd=0.6667 => 0.50 <= Rd < 0.90 => deltaP = -1.00
        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 1, 0, 0, 1.50, floor, ceiling);
        assertEquals(new BigDecimal("24.00"), newPrice);
    }

    @Test
    @DisplayName("Verify Floor Hard Stop (Current ₹18.00 - ₹2.00 = ₹18.00)")
    void testFloorHardStop() {
        BigDecimal currentPrice = new BigDecimal("18.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 0, 0, 0, 1.00, floor, ceiling);
        assertEquals(new BigDecimal("18.00"), newPrice);
    }

    @Test
    @DisplayName("Verify Ceiling Hard Stop (Current ₹35.00 + ₹1.00 = ₹35.00)")
    void testCeilingHardStop() {
        BigDecimal currentPrice = new BigDecimal("35.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");

        BigDecimal newPrice = calculateDWMAPrice(currentPrice, 10, 0, 0, 1.00, floor, ceiling);
        assertEquals(new BigDecimal("35.00"), newPrice);
    }

    @Test
    @DisplayName("Verify Null Floor/Ceiling Throws IllegalArgumentException in MarketCrashService")
    void testNullFloorOrCeilingRejection() {
        MarketCrashService service = new MarketCrashService(null, null, null, null, null, null);
        
        com.retailpos.domain.Product nullFloorProduct = new com.retailpos.domain.Product();
        nullFloorProduct.setId(99L);
        nullFloorProduct.setMinCupPrice(null);
        nullFloorProduct.setMaxCupPrice(new BigDecimal("35.00"));

        assertThrows(IllegalArgumentException.class, () -> {
            service.calculateCrashPrice(nullFloorProduct);
        });

        com.retailpos.domain.Product nullCeilingProduct = new com.retailpos.domain.Product();
        nullCeilingProduct.setId(99L);
        nullCeilingProduct.setMinCupPrice(new BigDecimal("18.00"));
        nullCeilingProduct.setMaxCupPrice(null);

        assertThrows(IllegalArgumentException.class, () -> {
            service.calculateCrashPrice(nullCeilingProduct);
        });
    }

    @Test
    @DisplayName("Verify DWMA Simulation Rules and Market Crash Snapshot Restoration")
    void testDWMASimulationAndCrashRestoration() {
        PricingSimulationService simService = new PricingSimulationService();
        PricingSimulationService.SimulationRequest req = new PricingSimulationService.SimulationRequest();
        req.setFlavourName("Fresh Mango Juice");
        req.setInitialPrice(new BigDecimal("25.00"));
        req.setMinPrice(new BigDecimal("18.00"));
        req.setMaxPrice(new BigDecimal("35.00"));
        req.setCupsPerInterval(4);
        req.setTargetSales(1.00);
        req.setTotalSimulatedPurchases(40);
        req.setIncludeCrash(true);

        PricingSimulationService.SimulationResponse res = simService.runSimulation(req);
        assertEquals(10, res.getSteps().size());

        // Step 1: W0=4, W1=0, W2=0 => S_w=4.00, R_d=4.00 => Movement +1 => 26.00
        PricingSimulationService.SimulationStep step1 = res.getSteps().get(0);
        assertEquals(4, step1.getW0());
        assertEquals(0, step1.getW1());
        assertEquals(0, step1.getW2());
        assertEquals(4.00, step1.getWeightedSales(), 0.01);
        assertEquals(4.00, step1.getDemandRatio(), 0.01);
        assertEquals("+₹1", step1.getPriceMovement());
        assertEquals(new BigDecimal("26.00"), step1.getPrice());

        // Step 5: Market Crash Injected -> Floor ₹18.00
        PricingSimulationService.SimulationStep step5 = res.getSteps().get(4);
        assertEquals("CRASH", step5.getPriceMovement());
        assertEquals(new BigDecimal("18.00"), step5.getPrice());

        // Step 6: Market Crash Expired -> Exact Pre-crash price snapshot restored
        PricingSimulationService.SimulationStep step6 = res.getSteps().get(5);
        assertEquals("RESTORED", step6.getPriceMovement());
        // Step 4 price before crash was 29.00
        assertEquals(new BigDecimal("29.00"), step6.getPrice());
    }
}

