package com.retailpos.notification;

import com.retailpos.domain.SystemNotification;
import com.retailpos.domain.SystemNotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@SuppressWarnings("null")
public class NotificationController {

    private final SystemNotificationRepository notificationRepository;

    public NotificationController(SystemNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<List<SystemNotification>> getNotifications() {
        return ResponseEntity.ok(notificationRepository.findByOrderByCreatedAtDesc());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("unreadCount", notificationRepository.countByIsReadFalse()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllAsRead() {
        List<SystemNotification> list = notificationRepository.findAll();
        list.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(list);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> dismissNotification(@PathVariable Long id) {
        if (notificationRepository.existsById(id)) {
            notificationRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/all")
    public ResponseEntity<Map<String, Object>> clearAllNotifications() {
        long count = notificationRepository.count();
        notificationRepository.deleteAll();
        return ResponseEntity.ok(Map.of("cleared", count, "message", "All notifications cleared"));
    }

    @PostMapping
    public ResponseEntity<SystemNotification> createNotification(@RequestBody SystemNotification notification) {
        if (notification.getType() == null) notification.setType("INFO");
        if (notification.getIsRead() == null) notification.setIsRead(false);
        return ResponseEntity.ok(notificationRepository.save(notification));
    }
}
