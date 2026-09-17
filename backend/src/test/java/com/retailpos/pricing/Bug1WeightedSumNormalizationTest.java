package com.retailpos.pricing;

import com.retailpos.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class Bug1WeightedSumNormalizationTest {

    private ProductRepository productRepository;
    private PriceHistoryRepository priceHistoryRepository;
    private SalesOrderItemRepository salesOrderItemRepository;
    private PriceAdjustmentService priceAdjustmentService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        priceHistoryRepository = mock(PriceHistoryRepository.class);
        salesOrderItemRepository = mock(SalesOrderItemRepository.class);

        priceAdjustmentService = new PriceAdjustmentService(
                productRepository,
                priceHistoryRepository,
                salesOrderItemRepository,
                null, // marketCrashService
                null, // pricingProcessedSaleRepository
                null, // redisRepository
                null  // pricingConfigurationService (defaults to W0=1.0, W1=0.5, W2=0.25, interval=60s)
        );
        PriceAdjustmentService.setMarketPaused(false);
    }

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

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        // All three windows W0, W1, W2 return stable sales equal to target
        when(salesOrderItemRepository.countQuantitySoldForProductBetweenExclusiveEnd(eq(productId), any(), any()))
                .thenReturn(stableSales);

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
