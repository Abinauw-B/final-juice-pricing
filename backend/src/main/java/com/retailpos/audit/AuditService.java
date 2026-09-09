package com.retailpos.audit;

import com.retailpos.domain.AuditLog;
import com.retailpos.domain.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Centralized Audit Logging Service.
 *
 * Provides a single entry point for recording audit events across the system.
 * All audit log writes are asynchronous to avoid impacting request latency.
 *
 * Standard action types:
 * - AUTH: LOGIN, LOGOUT, LOGIN_FAILED, PASSWORD_CHANGED
 * - ORDER: ORDER_CREATED, ORDER_FAILED
 * - PRICING: PRICE_CHANGED, SETTLEMENT_EXECUTED, PRICING_CONFIG_CHANGED
 * - MARKET: MARKET_CRASH_TRIGGERED, MARKET_CRASH_STOPPED, MARKET_PAUSED, MARKET_RESUMED
 * - PRODUCT: PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_DELETED
 * - INVENTORY: BATCH_REGISTERED, BATCH_DEPLETED
 * - ADMIN: PRICES_RESET, MANUAL_PRICE_OVERRIDE, USER_CREATED, USER_UPDATED
 * - SYSTEM: SYSTEM_STARTUP, CONFIG_CHANGED
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Record an audit event asynchronously.
     *
     * @param userId    The ID of the user who triggered the action (null for system events)
     * @param action    The action type (e.g. "LOGIN", "ORDER_CREATED")
     * @param module    The module name (e.g. "AUTH", "POS", "PRICING")
     * @param details   Human-readable description of what happened
     */
    @Async
    public void logEvent(Long userId, String action, String module, String details) {
        logEvent(userId, action, module, details, null);
    }

    /**
     * Record an audit event asynchronously with IP address.
     */
    @Async
    public void logEvent(Long userId, String action, String module, String details, String ipAddress) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId != null ? userId : 0L)
                    .action(action != null ? action : "UNKNOWN")
                    .module(module != null ? module : "SYSTEM")
                    .details(details != null ? details : "")
                    .ipAddress(ipAddress)
                    .build();
            auditLogRepository.save(auditLog);
            log.debug("[AUDIT] {} | {} | {} | {}", module, action, userId, details);
        } catch (Exception e) {
            // Audit logging should never break the main flow
            log.warn("[AUDIT] Failed to persist audit log: {}", e.getMessage());
        }
    }

    /**
     * Convenience methods for common event types.
     */
    public void logLogin(String username, Long userId, String ipAddress) {
        logEvent(userId, "LOGIN", "AUTH", "User '" + username + "' logged in successfully", ipAddress);
    }

    public void logLoginFailed(String username, String ipAddress) {
        logEvent(0L, "LOGIN_FAILED", "AUTH", "Failed login attempt for user '" + username + "'", ipAddress);
    }

    public void logOrderCreated(Long userId, String orderNumber, String totalAmount, int itemCount) {
        logEvent(userId, "ORDER_CREATED", "POS",
                String.format("Order %s created: %d items, total ₹%s", orderNumber, itemCount, totalAmount));
    }

    public void logPriceChanged(Long productId, String productName, String oldPrice, String newPrice, String reason) {
        logEvent(0L, "PRICE_CHANGED", "PRICING",
                String.format("Product '%s' (ID=%d): ₹%s → ₹%s [%s]", productName, productId, oldPrice, newPrice, reason));
    }

    public void logMarketCrashTriggered(String crashCode, String triggerSource) {
        logEvent(0L, "MARKET_CRASH_TRIGGERED", "MARKET",
                String.format("Market crash triggered: code=%s, source=%s", crashCode, triggerSource));
    }

    public void logMarketCrashStopped(String crashCode) {
        logEvent(0L, "MARKET_CRASH_STOPPED", "MARKET",
                String.format("Market crash stopped: code=%s, prices restored", crashCode));
    }

    public void logPricesReset(String actor, int productCount) {
        logEvent(0L, "PRICES_RESET", "ADMIN",
                String.format("All %d product prices reset to defaults by %s", productCount, actor));
    }

    public void logProductCreated(String productName, Long productId) {
        logEvent(0L, "PRODUCT_CREATED", "PRODUCT",
                String.format("Product '%s' (ID=%d) created", productName, productId));
    }

    public void logProductUpdated(String productName, Long productId) {
        logEvent(0L, "PRODUCT_UPDATED", "PRODUCT",
                String.format("Product '%s' (ID=%d) updated", productName, productId));
    }

    public void logProductDeleted(Long productId) {
        logEvent(0L, "PRODUCT_DELETED", "PRODUCT",
                String.format("Product ID=%d deleted", productId));
    }

    public void logSettlementExecuted(String executionId, int updatedCount, int unchangedCount) {
        logEvent(0L, "SETTLEMENT_EXECUTED", "PRICING",
                String.format("Settlement %s: %d updated, %d unchanged", executionId, updatedCount, unchangedCount));
    }

    public void logBatchRegistered(Long productId, int volumeMl) {
        logEvent(0L, "BATCH_REGISTERED", "INVENTORY",
                String.format("New %dml batch registered for product ID=%d", volumeMl, productId));
    }

    public void logConfigChanged(String configKey, String oldValue, String newValue, String actor) {
        logEvent(0L, "CONFIG_CHANGED", "SYSTEM",
                String.format("Config '%s' changed: %s → %s by %s", configKey, oldValue, newValue, actor));
    }
}
