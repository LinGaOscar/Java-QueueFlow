package com.example.queueflow.infrastructure;

import com.example.queueflow.domain.QueueAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueAuditLogRepository extends JpaRepository<QueueAuditLog, Long> {
}
