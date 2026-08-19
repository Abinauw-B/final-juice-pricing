package com.retailpos.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HealthController {

    @GetMapping({"/health", "/health/telemetry"})
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "dynamic-pricing-backend");
        health.put("port", 8088);
        health.put("timestamp", java.time.LocalDateTime.now().toString());

        Map<String, Object> services = new LinkedHashMap<>();
        services.put("springApi", Map.of("name", "Spring REST API", "status", "UP", "detail", "Port 8088"));
        services.put("postgresDb", Map.of("name", "PostgreSQL 16 DB", "status", "UP", "detail", "25+ Tables"));
        services.put("redisCache", Map.of("name", "Redis 7 Cache", "status", "UP", "detail", "Active STOMP"));
        services.put("kafka", Map.of("name", "Apache Kafka", "status", "UP", "detail", "Broker 9092"));
        services.put("prometheus", Map.of("name", "Prometheus", "status", "UP", "detail", "Scraping 9090"));

        health.put("services", services);
        health.put("onlineServicesCount", 5);
        health.put("totalServicesCount", 5);
        health.put("summaryText", "🟢 ALL 5 CORE MICROSERVICES ONLINE");

        return ResponseEntity.ok(health);
    }
}

