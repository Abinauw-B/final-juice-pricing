package com.retailpos.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Instant;

@Configuration
@EnableScheduling
public class DynamicPricingSchedulerConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DynamicPricingSchedulerConfig.class);

    private final PricingEngineService pricingEngineService;
    private final PricingConfigurationService pricingConfigService;

    public DynamicPricingSchedulerConfig(PricingEngineService pricingEngineService, PricingConfigurationService pricingConfigService) {
        this.pricingEngineService = pricingEngineService;
        this.pricingConfigService = pricingConfigService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                () -> {
                    try {
                        pricingEngineService.executeSettlementCycle(false);
                    } catch (Exception e) {
                        log.error("[DYNAMIC SCHEDULER] Error executing dynamic settlement cycle: {}", e.getMessage(), e);
                    }
                },
                triggerContext -> {
                    int intervalSeconds = pricingConfigService.getSettlementIntervalSeconds();
                    if (intervalSeconds <= 0) intervalSeconds = 60;
                    Instant lastActual = triggerContext.lastActualExecution();
                    if (lastActual == null) {
                        return Instant.now().plusSeconds(intervalSeconds);
                    }
                    return lastActual.plusSeconds(intervalSeconds);
                }
        );
        log.info("[DYNAMIC SCHEDULER] Dynamic DWMA Settlement Scheduler registered successfully with live interval trigger.");
    }
}
