package com.retailpos.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
@SuppressWarnings("null")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed-origins:https://final-juice-pricing-admin.vercel.app,https://final-juice-pricing.vercel.app,http://localhost:8000,http://localhost:8001,http://localhost:8002}")
    private String allowedOrigins;

    @org.springframework.context.annotation.Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler wsBrokerTaskScheduler() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(@org.springframework.lang.NonNull MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
            .setHeartbeatValue(new long[]{10000, 10000})
            .setTaskScheduler(wsBrokerTaskScheduler());
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@org.springframework.lang.NonNull StompEndpointRegistry registry) {
        java.util.List<String> originList = new java.util.ArrayList<>(java.util.List.of(
            "https://final-juice-pricing.vercel.app",
            "https://final-juice-pricing-admin.vercel.app",
            "https://*.vercel.app",
            "http://localhost:*",
            "http://127.0.0.1:*"
        ));
        if (allowedOrigins != null && !allowedOrigins.trim().isEmpty()) {
            for (String origin : allowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("*") && !originList.contains(trimmed)) {
                    originList.add(trimmed);
                }
            }
        }
        String[] origins = originList.toArray(new String[0]);

        // Register endpoints with SockJS fallback and pure WSS support
        registry.addEndpoint("/ws", "/ws/prices", "/ws/pos")
            .setAllowedOriginPatterns(origins)
            .withSockJS();

        registry.addEndpoint("/ws", "/ws/prices", "/ws/pos")
            .setAllowedOriginPatterns(origins);
    }
}
