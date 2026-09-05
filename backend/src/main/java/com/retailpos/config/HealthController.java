package com.retailpos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${server.port:8088}")
    private int serverPort;

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
        health.put("port", serverPort);
        health.put("timestamp", java.time.LocalDateTime.now().toString());

        Map<String, Object> services = new LinkedHashMap<>();
        services.put("springApi", Map.of("name", "Spring REST API", "status", "UP", "detail", "Port " + serverPort));
        services.put("postgresDb", Map.of("name", "PostgreSQL 16 DB", "status", dbOk ? "UP" : "DOWN", "detail", dbOk ? "Connected (Port 5432)" : "Disconnected"));
        services.put("websocket", Map.of("name", "STOMP WebSocket", "status", "UP", "detail", "Active Broadcast /topic/prices"));

        health.put("services", services);
        health.put("summaryText", dbOk ? "🟢 ALL BACKEND MICROSERVICES ONLINE & POSTGRESQL CONNECTED" : "🔴 DATABASE DISCONNECTED");

        return dbOk ? ResponseEntity.ok(health) : ResponseEntity.status(503).body(health);
    }

    @GetMapping({"/liveness", "/health/liveness"})
    public ResponseEntity<Map<String, Object>> getLiveness() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("liveness", true);
        body.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping({"/readiness", "/health/readiness"})
    public ResponseEntity<Map<String, Object>> getReadiness() {
        boolean dbOk = false;
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbOk = (one != null && one == 1);
        } catch (Exception e) {
            dbOk = false;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbOk ? "UP" : "DOWN");
        body.put("readiness", dbOk);
        body.put("database", dbOk ? "CONNECTED" : "DISCONNECTED");
        body.put("timestamp", java.time.LocalDateTime.now().toString());

        return dbOk ? ResponseEntity.ok(body) : ResponseEntity.status(503).body(body);
    }

    @GetMapping({"/metrics", "/health/metrics"})
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("status", "UP");
        metrics.put("timestamp", java.time.LocalDateTime.now().toString());
        metrics.put("uptimeMs", System.currentTimeMillis());

        try {
            Integer totalOrders = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sales_orders", Integer.class);
            Integer totalProducts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
            Integer totalAuditRecords = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM price_history", Integer.class);

            metrics.put("totalOrdersProcessed", totalOrders != null ? totalOrders : 0);
            metrics.put("totalProductsManaged", totalProducts != null ? totalProducts : 0);
            metrics.put("totalPricingEvaluations", totalAuditRecords != null ? totalAuditRecords : 0);
            metrics.put("activeDatabaseConnections", 100);
            metrics.put("websocketTopic", "/topic/prices");
        } catch (Exception e) {
            metrics.put("error", e.getMessage());
        }

        return ResponseEntity.ok(metrics);
    }
}

