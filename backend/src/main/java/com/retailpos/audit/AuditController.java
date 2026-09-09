package com.retailpos.audit;

import com.retailpos.domain.AuditLog;
import com.retailpos.domain.AuditLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/audit-logs", "/api/audit/logs"})
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    public AuditController(AuditLogRepository auditLogRepository, AuditService auditService) {
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        try {
            List<AuditLog> logs;

            if (module != null && !module.isBlank() && action != null && !action.isBlank()) {
                logs = auditLogRepository.findByModuleAndActionOrderByCreatedAtDesc(module.toUpperCase(), action.toUpperCase());
            } else if (module != null && !module.isBlank()) {
                logs = auditLogRepository.findByModuleOrderByCreatedAtDesc(module.toUpperCase());
            } else if (action != null && !action.isBlank()) {
                logs = auditLogRepository.findByActionOrderByCreatedAtDesc(action.toUpperCase());
            } else {
                logs = auditLogRepository.findTop100ByOrderByIdDesc();
            }

            // Apply limit
            if (logs.size() > limit) {
                logs = logs.subList(0, limit);
            }

            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/modules")
    public ResponseEntity<Map<String, List<String>>> getAvailableModules() {
        return ResponseEntity.ok(Map.of(
                "modules", List.of("AUTH", "POS", "PRICING", "MARKET", "PRODUCT", "INVENTORY", "ADMIN", "SYSTEM"),
                "actions", List.of("LOGIN", "LOGIN_FAILED", "LOGOUT", "ORDER_CREATED", "PRICE_CHANGED",
                        "SETTLEMENT_EXECUTED", "MARKET_CRASH_TRIGGERED", "MARKET_CRASH_STOPPED",
                        "PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_DELETED",
                        "PRICES_RESET", "CONFIG_CHANGED", "BATCH_REGISTERED")
        ));
    }

    @PostMapping
    public ResponseEntity<AuditLog> createAuditLog(@RequestBody AuditLog log) {
        if (log.getAction() == null || log.getAction().isBlank()) log.setAction("SYSTEM_ACTION");
        if (log.getModule() == null || log.getModule().isBlank()) log.setModule("SYSTEM");
        if (log.getUserId() == null) log.setUserId(1L);
        return ResponseEntity.ok(auditLogRepository.save(log));
    }
}
