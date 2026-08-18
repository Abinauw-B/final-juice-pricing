package com.retailpos.pricing;

import com.retailpos.domain.JuiceBatch;
import com.retailpos.domain.JuiceBatchRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class StockPressureService {

    private final JuiceBatchRepository juiceBatchRepository;

    public StockPressureService(JuiceBatchRepository juiceBatchRepository) {
        this.juiceBatchRepository = juiceBatchRepository;
    }

    public enum PressureLevel {
        LOW, NORMAL, HIGH, VERY_HIGH
    }

    public double calculateStockPressurePercentage(Long productId) {
        Optional<JuiceBatch> activeBatchOpt = juiceBatchRepository.findFirstActiveBatchForProduct(productId);
        if (activeBatchOpt.isEmpty()) {
            return 100.0; // 100% stock pressure if no active batch
        }
        JuiceBatch batch = activeBatchOpt.get();
        if (batch.getInitialVolumeMl() <= 0) {
            return 100.0;
        }
        double remainingPct = ((double) batch.getRemainingVolumeMl() / batch.getInitialVolumeMl()) * 100.0;
        double pressurePct = 100.0 - remainingPct;
        return Math.max(0.0, Math.min(100.0, pressurePct));
    }

    public double calculateStockPressureScore(double pressurePct) {
        // 0% pressure (fresh batch) -> 50.0 neutral baseline
        // 100% pressure (fully depleted) -> 100.0 maximum pressure
        return 50.0 + (pressurePct * 0.5);
    }

    public PressureLevel getPressureLevel(double pressurePct) {
        if (pressurePct >= 90.0) {
            return PressureLevel.VERY_HIGH;
        } else if (pressurePct >= 70.0) {
            return PressureLevel.HIGH;
        } else if (pressurePct >= 40.0) {
            return PressureLevel.NORMAL;
        } else {
            return PressureLevel.LOW;
        }
    }
}
