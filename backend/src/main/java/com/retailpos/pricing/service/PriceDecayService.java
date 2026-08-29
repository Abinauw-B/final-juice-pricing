package com.retailpos.pricing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceDecayService {

    private static final Logger log = LoggerFactory.getLogger(PriceDecayService.class);

    public PriceDecayService() {
    }

    /**
     * Out-of-band decay is disabled; zero-demand price decay is governed strictly by the
     * authoritative 2-minute DWMA settlement engine (PricingEngineService / PriceAdjustmentService).
     */
    @Transactional
    public void checkForPriceDecay() {
        log.debug("Out-of-band decay check called; decay is governed authoritatively by the 2-minute DWMA settlement engine.");
    }
}
