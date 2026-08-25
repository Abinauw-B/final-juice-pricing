package com.retailpos.pricing.service;

import com.retailpos.domain.ProductRepository;
import com.retailpos.pricing.PricingEngineService.ProductPriceDTO;
import com.retailpos.pricing.model.MarketPriceUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PriceBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(PriceBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ProductRepository productRepository;

    public PriceBroadcastService(SimpMessagingTemplate messagingTemplate, ProductRepository productRepository) {
        this.messagingTemplate = messagingTemplate;
        this.productRepository = productRepository;
    }

    public void broadcastPriceUpdate(int marketVersion, List<ProductPriceDTO> updatedProducts) {
        if (messagingTemplate == null || updatedProducts == null || updatedProducts.isEmpty()) {
            return;
        }

        try {
            MarketPriceUpdate update = MarketPriceUpdate.builder()
                    .type("PRICE_UPDATE")
                    .marketVersion(marketVersion)
                    .timestamp(LocalDateTime.now().toString())
                    .changes(updatedProducts)
                    .build();

            messagingTemplate.convertAndSend("/topic/prices", update);
            messagingTemplate.convertAndSend("/topic/settlement", update);
            messagingTemplate.convertAndSend("/topic/led-display", update);
            messagingTemplate.convertAndSend("/topic/products", productRepository.findByIsActiveTrueOrderByIdAsc());

            log.info("📡 [WEBSOCKET BROADCAST] Published Market Version {} with {} product price changes to /topic/prices",
                    marketVersion, updatedProducts.size());
        } catch (Exception e) {
            log.warn("WebSocket price broadcast failed: {}", e.getMessage());
        }
    }

    public void broadcastMarketCrash(int marketVersion, List<ProductPriceDTO> resetPrices) {
        if (messagingTemplate == null) return;

        try {
            Map<String, Object> crashPayload = new HashMap<>();
            crashPayload.put("type", "MARKET_CRASH");
            crashPayload.put("marketVersion", marketVersion);
            crashPayload.put("timestamp", LocalDateTime.now().toString());
            crashPayload.put("message", "🚨 GLOBAL MARKET CRASH TRIGGERED! Prices reset to floor/baseline.");
            crashPayload.put("changes", resetPrices);

            messagingTemplate.convertAndSend("/topic/market-crash", crashPayload);
            messagingTemplate.convertAndSend("/topic/prices", crashPayload);
            messagingTemplate.convertAndSend("/topic/led-display", crashPayload);
            messagingTemplate.convertAndSend("/topic/products", productRepository.findByIsActiveTrueOrderByIdAsc());

            log.info("🚨 [WEBSOCKET BROADCAST] Market Crash Event Broadcasted to all channels!");
        } catch (Exception e) {
            log.warn("WebSocket market crash broadcast failed: {}", e.getMessage());
        }
    }
}
