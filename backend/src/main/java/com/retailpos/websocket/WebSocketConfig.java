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
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed-origins:https://final-juice-pricing-admin.vercel.app,https://final-juice-pricing.vercel.app,http://localhost:8000,http://localhost:8001,http://localhost:8002}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(@org.springframework.lang.NonNull MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@org.springframework.lang.NonNull StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty() && !s.equals("*"))
            .toArray(String[]::new);

        if (origins.length == 0) {
            origins = new String[]{
                "https://final-juice-pricing-admin.vercel.app",
                "https://final-juice-pricing.vercel.app"
            };
        }

        // Register endpoints with SockJS fallback and pure WSS support
        registry.addEndpoint("/ws", "/ws/prices", "/ws/pos")
            .setAllowedOriginPatterns(origins)
            .withSockJS();

        registry.addEndpoint("/ws", "/ws/prices", "/ws/pos")
            .setAllowedOriginPatterns(origins);
    }
}
