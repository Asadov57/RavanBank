package com.financeapp.repository;

import com.financeapp.model.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    List<AdminAuditLog> findAllByOrderByTimestampDesc();
    List<AdminAuditLog> findTop100ByOrderByTimestampDesc();
    List<AdminAuditLog> findByTargetTypeOrderByTimestampDesc(String targetType);
}

