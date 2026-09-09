package com.retailpos.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 / Swagger UI configuration.
 *
 * Swagger UI is available at:  http://localhost:8080/swagger-ui.html
 * OpenAPI JSON available at:   http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI juiceExchangeOpenAPI() {
        final String bearerSchemeName = "BearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Juice Bar Stock Exchange — Backend API")
                        .description("""
                                ## Dynamic Beverage Pricing Platform

                                Real-time stock-exchange-style dynamic pricing system for a juice bar.

                                ### Key Features
                                - **DWMA Pricing Engine** — 60-second Dynamic Weighted Moving Average settlement cycles
                                - **Market Crash Routine** — global floor-price event triggered by volume thresholds
                                - **POS Checkout** — server-authoritative price locks, inventory deduction, idempotent orders
                                - **20L Batch Inventory** — atomic volumetric deductions from juice batches
                                - **STOMP WebSocket** — real-time price broadcasts to all connected clients

                                ### Authentication
                                Most admin endpoints require a JWT Bearer token obtained via `POST /api/auth/login`.
                                Customer POS endpoints (`GET /api/products`, `POST /api/pos/checkout`) are public.

                                ### Price Rules
                                - Price moves in strict **±₹1.00** steps per 60-second settlement window
                                - Each product is **fully independent** — no cross-product correlations
                                - Floor and ceiling bounds are configurable per product
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Juice Bar Exchange Engineering")
                                .email("tech@juicebar.example.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://juicebar.example.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development"),
                        new Server()
                                .url("https://api.juicebar.example.com")
                                .description("Production")))
                .addSecurityItem(new SecurityRequirement().addList(bearerSchemeName))
                .components(new Components()
                        .addSecuritySchemes(bearerSchemeName,
                                new SecurityScheme()
                                        .name(bearerSchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token obtained from POST /api/auth/login")));
    }
}
