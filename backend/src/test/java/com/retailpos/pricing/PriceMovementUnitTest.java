package com.retailpos.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PriceMovementUnitTest {

    public static BigDecimal calculateVolatilityNewPrice(BigDecimal currentPrice, int orderCount, int targetOrders, BigDecimal volatility, BigDecimal floor, BigDecimal ceiling) {
        double demandRatio;
        BigDecimal rawChangePct;
        BigDecimal appliedChangePct;

        if (orderCount == 0) {
            demandRatio = 0.0;
            rawChangePct = volatility.negate();
            appliedChangePct = rawChangePct;
        } else {
            demandRatio = (double) (orderCount - targetOrders) / (double) targetOrders;
            rawChangePct = BigDecimal.valueOf(demandRatio).multiply(volatility);
            appliedChangePct = rawChangePct.max(volatility.negate()).min(volatility);
        }

        BigDecimal multiplier = BigDecimal.ONE.add(appliedChangePct);
        BigDecimal calculatedPrice = currentPrice.multiply(multiplier);
        return calculatedPrice.max(floor).min(ceiling).setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("Verify Zero Demand Decay (-Volatility = -8%)")
    void testZeroDemandDecay() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");
        BigDecimal volatility = new BigDecimal("0.0800");

        BigDecimal newPrice = calculateVolatilityNewPrice(currentPrice, 0, 5, volatility, floor, ceiling);
        // 25.00 * (1 - 0.08) = 23.00
        assertEquals(new BigDecimal("23.00"), newPrice);
    }

    @Test
    @DisplayName("Verify Target Demand Hit (5/5 orders = 0% Change)")
    void testTargetDemandHit() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");
        BigDecimal volatility = new BigDecimal("0.0800");

        BigDecimal newPrice = calculateVolatilityNewPrice(currentPrice, 5, 5, volatility, floor, ceiling);
        // (5-5)/5 = 0 ratio -> 0% change -> 25.00
        assertEquals(new BigDecimal("25.00"), newPrice);
    }

    @Test
    @DisplayName("Verify High Demand Surge Clamping (+8% Max)")
    void testHighDemandSurgeClamping() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");
        BigDecimal volatility = new BigDecimal("0.0800");

        // 25 orders vs 5 target -> demandRatio = +4.0 -> rawChange = 32% -> clamped to +8%
        BigDecimal newPrice = calculateVolatilityNewPrice(currentPrice, 25, 5, volatility, floor, ceiling);
        // 25.00 * 1.08 = 27.00
        assertEquals(new BigDecimal("27.00"), newPrice);
    }

    @Test
    @DisplayName("Verify Below Target Partial Decay (2/5 orders = -4.8%)")
    void testBelowTargetPartialDecay() {
        BigDecimal currentPrice = new BigDecimal("25.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");
        BigDecimal volatility = new BigDecimal("0.0800");

        // (2-5)/5 = -0.6 ratio -> rawChange = -0.6 * 0.08 = -0.048
        // 25.00 * (1 - 0.048) = 23.80
        BigDecimal newPrice = calculateVolatilityNewPrice(currentPrice, 2, 5, volatility, floor, ceiling);
        assertEquals(new BigDecimal("23.80"), newPrice);
    }

    @Test
    @DisplayName("Verify Price Floor Enforcement (Min ₹18.00)")
    void testPriceFloorEnforcement() {
        BigDecimal currentPrice = new BigDecimal("18.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");
        BigDecimal volatility = new BigDecimal("0.0800");

        // 0 orders -> -8% decay -> 18.00 * 0.92 = 16.56 -> clamped to floor 18.00
        BigDecimal newPrice = calculateVolatilityNewPrice(currentPrice, 0, 5, volatility, floor, ceiling);
        assertEquals(new BigDecimal("18.00"), newPrice);
    }

    @Test
    @DisplayName("Verify Price Ceiling Enforcement (Max ₹35.00)")
    void testPriceCeilingEnforcement() {
        BigDecimal currentPrice = new BigDecimal("35.00");
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");
        BigDecimal volatility = new BigDecimal("0.0800");

        // 10 orders vs 5 target -> +8% surge -> 35.00 * 1.08 = 37.80 -> clamped to ceiling 35.00
        BigDecimal newPrice = calculateVolatilityNewPrice(currentPrice, 10, 5, volatility, floor, ceiling);
        assertEquals(new BigDecimal("35.00"), newPrice);
    }

    public static BigDecimal calculateCrashPrice(BigDecimal floor, BigDecimal ceiling, BigDecimal bufferPercent) {
        BigDecimal multiplier = BigDecimal.ONE.add(bufferPercent);
        BigDecimal crashVal = floor.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        return crashVal.max(floor).min(ceiling);
    }

    @Test
    @DisplayName("Test 1 — Basic Crash Price (Floor ₹18 * 1.05 = ₹18.90)")
    void testBasicCrashPrice() {
        BigDecimal floor = new BigDecimal("18.00");
        BigDecimal ceiling = new BigDecimal("35.00");
        BigDecimal buffer = new BigDecimal("0.05");

        BigDecimal crashPrice = calculateCrashPrice(floor, ceiling, buffer);
        assertEquals(new BigDecimal("18.90"), crashPrice);
    }

    @Test
    @DisplayName("Test 2 — Floor Safety (Floor ₹20 * 1.05 = ₹21.00)")
    void testFloorSafetyCrashPrice() {
        BigDecimal floor = new BigDecimal("20.00");
        BigDecimal ceiling = new BigDecimal("35.00");
        BigDecimal buffer = new BigDecimal("0.05");

        BigDecimal crashPrice = calculateCrashPrice(floor, ceiling, buffer);
        assertEquals(new BigDecimal("21.00"), crashPrice);
    }

    @Test
    @DisplayName("Test 3 — Ceiling Safety (Floor ₹34 * 1.05 = ₹35.70 -> Clamped to ₹35.00)")
    void testCeilingSafetyCrashPrice() {
        BigDecimal floor = new BigDecimal("34.00");
        BigDecimal ceiling = new BigDecimal("35.00");
        BigDecimal buffer = new BigDecimal("0.05");

        BigDecimal crashPrice = calculateCrashPrice(floor, ceiling, buffer);
        assertEquals(new BigDecimal("35.00"), crashPrice);
    }

    @Test
    @DisplayName("Test 4 — Low Ceiling Safety (Floor ₹10 * 1.05 = ₹10.50 -> Clamped to Ceiling ₹10.40)")
    void testLowCeilingSafetyCrashPrice() {
        BigDecimal floor = new BigDecimal("10.00");
        BigDecimal ceiling = new BigDecimal("10.40");
        BigDecimal buffer = new BigDecimal("0.05");

        BigDecimal crashPrice = calculateCrashPrice(floor, ceiling, buffer);
        assertEquals(new BigDecimal("10.40"), crashPrice);
    }

    @Test
    @DisplayName("Verify Null Floor/Ceiling Throws IllegalArgumentException in MarketCrashService")
    void testNullFloorOrCeilingRejection() {
        MarketCrashService service = new MarketCrashService(null, null, null);
        
        com.retailpos.domain.Product nullFloorProduct = new com.retailpos.domain.Product();
        nullFloorProduct.setId(99L);
        nullFloorProduct.setMinCupPrice(null);
        nullFloorProduct.setMaxCupPrice(new BigDecimal("35.00"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            service.calculateCrashPrice(nullFloorProduct);
        });

        com.retailpos.domain.Product nullCeilingProduct = new com.retailpos.domain.Product();
        nullCeilingProduct.setId(99L);
        nullCeilingProduct.setMinCupPrice(new BigDecimal("18.00"));
        nullCeilingProduct.setMaxCupPrice(null);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            service.calculateCrashPrice(nullCeilingProduct);
        });
    }
}
