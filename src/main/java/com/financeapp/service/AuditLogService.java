package com.financeapp.service;

import com.financeapp.model.AdminAuditLog;
import com.financeapp.repository.AdminAuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AdminAuditLogRepository auditLogRepository;

    public AuditLogService(AdminAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logAction(String adminUsername, String action, String targetType, String targetId, String details) {
        try {
            AdminAuditLog log = new AdminAuditLog(
                    adminUsername != null ? adminUsername : "SYSTEM",
                    action,
                    targetType,
                    targetId,
                    details
            );
            auditLogRepository.save(log);
        } catch (Exception e) {

        }
    }
}

