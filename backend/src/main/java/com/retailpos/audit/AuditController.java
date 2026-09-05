package com.retailpos.audit;

import com.retailpos.domain.AuditLog;
import com.retailpos.domain.AuditLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/audit-logs", "/api/audit/logs"})
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        try {
            return ResponseEntity.ok(auditLogRepository.findTop100ByOrderByIdDesc());
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping
    public ResponseEntity<AuditLog> createAuditLog(@RequestBody AuditLog log) {
        if (log.getAction() == null || log.getAction().isBlank()) log.setAction("SYSTEM_ACTION");
        if (log.getModule() == null || log.getModule().isBlank()) log.setModule("SYSTEM");
        if (log.getUserId() == null) log.setUserId(1L);
        return ResponseEntity.ok(auditLogRepository.save(log));
    }
}
