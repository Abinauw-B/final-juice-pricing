package com.retailpos.notification;

import com.retailpos.domain.SystemNotification;
import com.retailpos.domain.SystemNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Centralized Notification Service.
 *
 * Creates system notifications and broadcasts them via WebSocket.
 * Notification types: INFO, WARNING, ALERT, SUCCESS, ERROR
 */
@Service
@SuppressWarnings("null")
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SystemNotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SystemNotificationRepository notificationRepository,
                                SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Create and persist a notification, then broadcast it via WebSocket.
     */
    @Async
    public void createNotification(String title, String message, String type) {
        try {
            SystemNotification notification = SystemNotification.builder()
                    .title(title)
                    .message(message)
                    .type(type != null ? type : "INFO")
                    .isRead(false)
                    .build();

            SystemNotification saved = notificationRepository.save(notification);

            // Broadcast to connected clients
            try {
                if (messagingTemplate != null) {
                    messagingTemplate.convertAndSend("/topic/notifications", saved);
                }
            } catch (Exception e) {
                log.debug("WebSocket notification broadcast bypass: {}", e.getMessage());
            }

            log.debug("[NOTIFICATION] Created: type={}, title='{}'", type, title);
        } catch (Exception e) {
            log.warn("[NOTIFICATION] Failed to create notification: {}", e.getMessage());
        }
    }

    // ---- Convenience methods for common notification scenarios ----

    public void notifyMarketCrashStarted(String crashCode, int durationSeconds) {
        createNotification(
                "🚨 Market Crash Activated",
                String.format("Market crash %s triggered. All prices set to floor for %d seconds.", crashCode, durationSeconds),
                "ALERT"
        );
    }

    public void notifyMarketCrashStopped(String crashCode) {
        createNotification(
                "🟢 Market Crash Ended",
                String.format("Market crash %s has ended. Pre-crash prices restored.", crashCode),
                "SUCCESS"
        );
    }

    public void notifyLowInventory(String productName, int remainingMl) {
        int remainingCups = remainingMl / 250;
        createNotification(
                "⚠️ Low Inventory Alert",
                String.format("'%s' has only %d cups (%dml) remaining. Consider registering a new batch.", productName, remainingCups, remainingMl),
                "WARNING"
        );
    }

    public void notifyBatchDepleted(String productName, Long batchId) {
        createNotification(
                "📦 Batch Depleted",
                String.format("Batch #%d for '%s' is fully depleted. A new batch should be registered.", batchId, productName),
                "WARNING"
        );
    }

    public void notifyPricesReset(String actor, int productCount) {
        createNotification(
                "🔄 Prices Reset",
                String.format("All %d product prices were reset to defaults by %s.", productCount, actor),
                "INFO"
        );
    }

    public void notifyNewProductAdded(String productName) {
        createNotification(
                "🆕 New Product Added",
                String.format("New juice '%s' has been added to the exchange.", productName),
                "SUCCESS"
        );
    }

    public void notifySettlementCompleted(int updatedCount, int totalProducts) {
        if (updatedCount > 0) {
            createNotification(
                    "📊 Settlement Complete",
                    String.format("Pricing settlement completed: %d of %d products updated.", updatedCount, totalProducts),
                    "INFO"
            );
        }
    }
}
