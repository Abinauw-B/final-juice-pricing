package com.retailpos.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping({"/health", "/health/telemetry"})
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();

        boolean dbOk = false;
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbOk = (one != null && one == 1);
        } catch (Exception e) {
            dbOk = false;
        }

        health.put("status", dbOk ? "UP" : "DOWN");
        health.put("database", dbOk ? "CONNECTED" : "DISCONNECTED");
        health.put("service", "dynamic-pricing-backend");
        health.put("port", 8088);
        health.put("timestamp", java.time.LocalDateTime.now().toString());

        Map<String, Object> services = new LinkedHashMap<>();
        services.put("springApi", Map.of("name", "Spring REST API", "status", "UP", "detail", "Port 8088"));
        services.put("postgresDb", Map.of("name", "PostgreSQL 16 DB", "status", dbOk ? "UP" : "DOWN", "detail", dbOk ? "Connected (Port 5432)" : "Disconnected"));
        services.put("websocket", Map.of("name", "STOMP WebSocket", "status", "UP", "detail", "Active Broadcast /topic/prices"));

        health.put("services", services);
        health.put("summaryText", dbOk ? "🟢 ALL BACKEND MICROSERVICES ONLINE & POSTGRESQL CONNECTED" : "🔴 DATABASE DISCONNECTED");

        return dbOk ? ResponseEntity.ok(health) : ResponseEntity.status(503).body(health);
    }
}

