package com.retailpos.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Instant;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "pricing.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DynamicPricingSchedulerConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DynamicPricingSchedulerConfig.class);

    private final PricingSettlementCoordinator pricingSettlementCoordinator;
    private final PricingConfigurationService pricingConfigService;

    public DynamicPricingSchedulerConfig(PricingSettlementCoordinator pricingSettlementCoordinator, PricingConfigurationService pricingConfigService) {
        this.pricingSettlementCoordinator = pricingSettlementCoordinator;
        this.pricingConfigService = pricingConfigService;
    }

    @Override
    public void configureTasks(@org.springframework.lang.NonNull ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                () -> {
                    try {
                        pricingSettlementCoordinator.executeScheduledSettlement();
                    } catch (Exception e) {
                        log.error("[DYNAMIC SCHEDULER] Error executing dynamic settlement cycle: {}", e.getMessage(), e);
                    }
                },
                triggerContext -> {
                    int intervalSeconds = pricingConfigService.getSettlementIntervalSeconds();
                    if (intervalSeconds <= 0) intervalSeconds = 60;
                    java.time.LocalDateTime nextTarget = pricingSettlementCoordinator.getNextSettlementTime();
                    if (nextTarget != null) {
                        Instant targetInstant = nextTarget.atZone(java.time.ZoneId.systemDefault()).toInstant();
                        if (targetInstant.isAfter(Instant.now())) {
                            return targetInstant;
                        } else {
                            return Instant.now();
                        }
                    }
                    Instant lastActual = triggerContext.lastActualExecution();
                    if (lastActual == null) {
                        return Instant.now().plusSeconds(intervalSeconds);
                    }
                    Instant nextFromLast = lastActual.plusSeconds(intervalSeconds);
                    if (nextFromLast.isBefore(Instant.now())) {
                        return Instant.now();
                    }
                    return nextFromLast;
                }
        );
        log.info("[DYNAMIC SCHEDULER] Dynamic DWMA Settlement Scheduler registered successfully with live interval trigger.");
    }
}
