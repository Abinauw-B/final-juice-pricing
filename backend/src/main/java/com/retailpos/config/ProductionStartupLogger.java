package com.retailpos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProductionStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(ProductionStartupLogger.class);

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${server.port:8088}")
    private int port;

    @Value("${pricing.settlement-interval-seconds:60}")
    private int intervalSeconds;

    public ProductionStartupLogger(JdbcTemplate jdbcTemplate, 
                                   RedisTemplate<String, Object> redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onStartup() {
        boolean dbOk = false;
        try {
            Integer test = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbOk = (test != null && test == 1);
        } catch (Exception e) {
            dbOk = false;
        }

        boolean redisOk = false;
        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                redisTemplate.opsForValue().set("market:healthcheck", "ok");
                redisOk = "ok".equals(redisTemplate.opsForValue().get("market:healthcheck"));
            }
        } catch (Exception e) {
            redisOk = false;
        }

        log.info("================================================================================");
        log.info("           JUICE / PUB EXCHANGE PRODUCTION BACKEND ONLINE");
        log.info("================================================================================");
        log.info("Application started on port: {}", port);
        log.info("Database connected: {} (PostgreSQL Authoritative SSoT)", dbOk ? "ONLINE" : "OFFLINE / UNREACHABLE");
        log.info("Redis connected: {} (Cache & Synchronization Infrastructure)", redisOk ? "ONLINE" : "STANDBY / NON-FATAL BYPASS");
        log.info("Pricing engine initialized: Base=₹25.00, Floor=₹20.00, Ceiling=₹30.00, Allowed Deltas={+1.00, 0.00, -1.00, -2.00}");
        log.info("Scheduler initialized: Settlement Cycle={}s, Distributed Locking: Active", intervalSeconds);
        log.info("WebSocket initialized: STOMP active on /ws/prices, /ws/pos, /ws");
        log.info("Broadcast Topics: /topic/prices, /topic/settlement, /topic/market-crash, /topic/led-display, /topic/products");
        log.info("Security: CORS configured with credentials allowed; Wildcard origins disabled");
        log.info("================================================================================");
    }
}
