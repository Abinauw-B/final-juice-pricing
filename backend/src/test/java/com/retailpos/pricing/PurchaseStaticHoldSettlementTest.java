package com.retailpos.pricing;

import com.retailpos.domain.PriceHistoryRepository;
import com.retailpos.domain.Product;
import com.retailpos.domain.ProductRepository;
import com.retailpos.domain.SalesOrderItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PurchaseStaticHoldSettlementTest {

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
        p.setPriceVersion(1);
        p.setOrderCount(0);
        return p;
    }

    private PriceAdjustmentService createTestService(Product product, int mockSalesCount) {
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
                        return mockSalesCount;
                    }
                    return 0;
                }
        );

        PriceHistoryRepository historyRepo = (PriceHistoryRepository) Proxy.newProxyInstance(
                PriceHistoryRepository.class.getClassLoader(),
                new Class<?>[]{PriceHistoryRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("save")) {
                        return args[0];
                    }
                    return null;
                }
        );

        return new PriceAdjustmentService(
                productRepo,
                historyRepo,
                salesRepo,
                null, null, null, null
        );
    }

    private PricingConfigurationService.PricingConfigSnapshot createSnapshot(int intervalSec) {
        return new PricingConfigurationService.PricingConfigSnapshot(
                1L, intervalSec, new BigDecimal("1.0000"), new BigDecimal("0.5000"), new BigDecimal("0.2500"),
                new BigDecimal("1.1000"), new BigDecimal("0.9000"), new BigDecimal("1.1000"), new BigDecimal("0.5000"),
                new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("25.00"), new BigDecimal("20.00"),
                new BigDecimal("35.00"), new BigDecimal("20.00"), 180
        );
    }

    @Test
    @DisplayName("1. Product purchase registration sets exactly 2 settlement cycles of static hold")
    void testPurchaseRegistration_Sets2CyclesAndStaticHoldActive() {
        Product product = createMockProduct(101L, new BigDecimal("25.00"), new BigDecimal("20.00"), new BigDecimal("35.00"), 1.0);
        PriceAdjustmentService service = createTestService(product, 5);

        assertFalse(service.isProductUnderPurchaseStaticHold(101L));
        assertEquals(0, service.getPurchaseStaticCyclesRemaining(101L));

        service.registerProductPurchase(101L);

        assertTrue(service.isProductUnderPurchaseStaticHold(101L));
        assertEquals(2, service.getPurchaseStaticCyclesRemaining(101L));
    }

    @Test
    @DisplayName("2. Price retains static (deltaP = 0) for Cycle 1 and Cycle 2, then dynamic DWMA resumes on Cycle 3")
    void testTwoCycleStaticHoldEvaluationFlow() {
        // Product initially at ₹26.00 with high demand (10 sales vs target 1.0)
        Product product = createMockProduct(101L, new BigDecimal("26.00"), new BigDecimal("20.00"), new BigDecimal("35.00"), 1.0);
        PriceAdjustmentService service = createTestService(product, 10);
        PricingConfigurationService.PricingConfigSnapshot snapshot = createSnapshot(60);
        LocalDateTime now = LocalDateTime.now();

        // Customer purchases product -> triggers 2-cycle static hold
        service.registerProductPurchase(101L);
        assertEquals(2, service.getPurchaseStaticCyclesRemaining(101L));

        // --- SETTLEMENT CYCLE 1 ---
        PriceAdjustmentService.PriceEvaluationResult cycle1 = service.evaluateAndAdjustPrice(101L, now, snapshot, "CYCLE-1");

        assertEquals(0, new BigDecimal("26.00").compareTo(cycle1.getNewPrice()), "Cycle 1 price must retain static at ₹26.00");
        assertEquals(0, BigDecimal.ZERO.compareTo(cycle1.getPriceChange()), "Cycle 1 price delta must strictly be 0.00");
        assertFalse(cycle1.isPriceChanged(), "Cycle 1 priceChanged must be false");
        assertEquals("PURCHASE_STATIC_HOLD", cycle1.getDemandLevelCategory());
        assertEquals("PURCHASE_STATIC_CYCLE_1", cycle1.getStatusReason());
        // Remaining cycles should have decremented from 2 to 1
        assertEquals(1, service.getPurchaseStaticCyclesRemaining(101L));
        assertTrue(service.isProductUnderPurchaseStaticHold(101L));

        // --- SETTLEMENT CYCLE 2 ---
        PriceAdjustmentService.PriceEvaluationResult cycle2 = service.evaluateAndAdjustPrice(101L, now.plusMinutes(1), snapshot, "CYCLE-2");

        assertEquals(0, new BigDecimal("26.00").compareTo(cycle2.getNewPrice()), "Cycle 2 price must retain static at ₹26.00");
        assertEquals(0, BigDecimal.ZERO.compareTo(cycle2.getPriceChange()), "Cycle 2 price delta must strictly be 0.00");
        assertFalse(cycle2.isPriceChanged(), "Cycle 2 priceChanged must be false");
        assertEquals("PURCHASE_STATIC_HOLD", cycle2.getDemandLevelCategory());
        assertEquals("PURCHASE_STATIC_CYCLE_2", cycle2.getStatusReason());
        // Remaining cycles should have decremented from 1 to 0
        assertEquals(0, service.getPurchaseStaticCyclesRemaining(101L));
        assertFalse(service.isProductUnderPurchaseStaticHold(101L));

        // --- SETTLEMENT CYCLE 3 (Dynamic DWMA resumes) ---
        PriceAdjustmentService.PriceEvaluationResult cycle3 = service.evaluateAndAdjustPrice(101L, now.plusMinutes(2), snapshot, "CYCLE-3");

        // High demand should now execute normal dynamic price increase from ₹26.00 -> ₹27.00
        assertEquals(0, new BigDecimal("27.00").compareTo(cycle3.getNewPrice()), "Cycle 3 dynamic DWMA must resume and update price to ₹27.00");
        assertEquals(0, new BigDecimal("1.00").compareTo(cycle3.getPriceChange()), "Cycle 3 price delta must be +₹1.00");
        assertTrue(cycle3.isPriceChanged(), "Cycle 3 priceChanged must be true");
        assertEquals("HIGH", cycle3.getDemandLevelCategory());
    }

    @Test
    @DisplayName("3. Batch product purchase registration sets hold on all purchased items")
    void testBatchPurchaseRegistration() {
        Product product = createMockProduct(1L, new BigDecimal("25.00"), new BigDecimal("20.00"), new BigDecimal("35.00"), 1.0);
        PriceAdjustmentService service = createTestService(product, 2);

        Set<Long> productIds = Set.of(1L, 2L, 3L);
        service.registerProductPurchases(productIds);

        for (Long id : productIds) {
            assertTrue(service.isProductUnderPurchaseStaticHold(id));
            assertEquals(2, service.getPurchaseStaticCyclesRemaining(id));
        }

        service.clearPurchaseStaticHold(2L);
        assertFalse(service.isProductUnderPurchaseStaticHold(2L));
        assertTrue(service.isProductUnderPurchaseStaticHold(1L));
        assertTrue(service.isProductUnderPurchaseStaticHold(3L));

        service.clearAllPurchaseStaticHolds();
        assertFalse(service.isProductUnderPurchaseStaticHold(1L));
        assertFalse(service.isProductUnderPurchaseStaticHold(3L));
    }
}
