package com.retailpos.pricing;

import com.retailpos.domain.SalesOrderItemRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class DemandCalculationService {

    private final SalesOrderItemRepository salesOrderItemRepository;
    private final StockPressureService stockPressureService;
    private final TimeFactorService timeFactorService;

    public DemandCalculationService(SalesOrderItemRepository salesOrderItemRepository, StockPressureService stockPressureService, TimeFactorService timeFactorService) {
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.stockPressureService = stockPressureService;
        this.timeFactorService = timeFactorService;
    }

    public double calculateVelocityScore(Long productId, int windowMinutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
        Integer cupsSold = salesOrderItemRepository.countQuantitySoldForProductSince(productId, since);
        if (cupsSold == null || cupsSold <= 0) {
            return 50.0;
        }
        // Scale 0 cups -> 50.0 neutral baseline, 10 cups -> 100.0 max demand
        double score = 50.0 + ((cupsSold / 10.0) * 50.0);
        return Math.min(100.0, score);
    }

    public double calculateDemandScore(Long productId, double weightVelocity, double weightStockPressure, double weightTimeFactor, LocalTime currentTime) {
        double velocityScore = calculateVelocityScore(productId, 15);
        double stockPressurePct = stockPressureService.calculateStockPressurePercentage(productId);
        double stockPressureScore = stockPressureService.calculateStockPressureScore(stockPressurePct);
        double timeFactorScore = timeFactorService.getTimeFactorScore(currentTime);

        // Neutral baseline score of 50.0 when velocity is 0 and stock/time are at neutral defaults
        double demandScore = (weightVelocity * velocityScore) 
                           + (weightStockPressure * stockPressureScore) 
                           + (weightTimeFactor * timeFactorScore);

        return Math.max(0.0, Math.min(100.0, demandScore));
    }
}
