package com.retailpos.pricing;

import com.retailpos.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class Bug1WeightedSumNormalizationTest {

    @Test
    @DisplayName("BUG 1 REPRODUCTION: Stable demand w0=w1=w2=target must produce Rd in stable band [0.90, 1.10] and deltaP = 0")
    void testStableDemand_ProducesRdNearOne_AndZeroPriceChange() {
        Long productId = 101L;
        double targetSalesPerMin = 4.0; // target = 4 cups / 60s
        int stableSales = 4;            // w0 = 4, w1 = 4, w2 = 4

        Product product = new Product();
        product.setId(productId);
        product.setName("Fresh Mango Juice");
        product.setFlavour("FRESH_MANGO_JUICE");
        product.setCurrentCupPrice(new BigDecimal("25.00"));
        product.setDefaultCupPrice(new BigDecimal("25.00"));
        product.setMinCupPrice(new BigDecimal("20.00"));
        product.setMaxCupPrice(new BigDecimal("30.00"));
        product.setTargetSalesPer1Minute(targetSalesPerMin);
        product.setPricingMode("DYNAMIC");
        product.setWeightedSales(null);
        product.setOrderCount(stableSales);

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
                    return null;
                }
        );

        SalesOrderItemRepository salesRepo = (SalesOrderItemRepository) Proxy.newProxyInstance(
                SalesOrderItemRepository.class.getClassLoader(),
                new Class<?>[]{SalesOrderItemRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().contains("count")) {
                        return stableSales;
                    }
                    return 0;
                }
        );

        PriceHistoryRepository historyRepo = (PriceHistoryRepository) Proxy.newProxyInstance(
                PriceHistoryRepository.class.getClassLoader(),
                new Class<?>[]{PriceHistoryRepository.class},
                (proxy, method, args) -> null
        );

        PriceAdjustmentService priceAdjustmentService = new PriceAdjustmentService(
                productRepo,
                historyRepo,
                salesRepo,
                null,
                null,
                null,
                null
        );
        PriceAdjustmentService.setMarketPaused(false);

        LocalDateTime evaluationTime = LocalDateTime.now();
        PriceAdjustmentService.PriceEvaluationResult result = priceAdjustmentService.evaluateAndAdjustPrice(productId, evaluationTime);

        System.out.println("=== BUG 1 TEST OUTPUT ===");
        System.out.println("Target Sales (Normalized): " + result.getTargetSales());
        System.out.println("Weighted Sales (Sw):       " + result.getWeightedSales());
        System.out.println("Demand Ratio (Rd):         " + result.getDemandRatio());
        System.out.println("Price Change (deltaP):     " + result.getPriceChange());
        System.out.println("New Price:                 " + result.getNewPrice());
        System.out.println("Demand Category:           " + result.getDemandLevelCategory());
        System.out.println("Explanation:               " + result.getExplanation());
        System.out.println("=========================");

        // When w0 = w1 = w2 = target, Rd should equal 1.00 (within stable band 0.90 - 1.10)
        assertTrue(result.getDemandRatio() >= 0.90 && result.getDemandRatio() <= 1.10,
                "Under stable demand where w0=w1=w2=target, Rd must be within [0.90, 1.10]. Actual Rd was: " + result.getDemandRatio());

        // Price change must be 0.00 under stable demand
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getPriceChange()),
                "Under stable demand, price change deltaP must be 0.00. Actual was: " + result.getPriceChange());

        assertEquals(new BigDecimal("25.00"), result.getNewPrice(),
                "Price should remain steady at ₹25.00 under stable demand");

        assertEquals("NORMAL", result.getDemandLevelCategory(),
                "Demand category must be NORMAL under stable demand");
    }
}
