package com.example.queueflow.messaging;

import com.example.queueflow.domain.QueueAuditLog;
import com.example.queueflow.infrastructure.QueueAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumer.class);

    private final QueueAuditLogRepository auditLogRepository;

    public AuditConsumer(QueueAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @KafkaListener(
            topics = {
                "${app.kafka.topic.queue-created}",
                "${app.kafka.topic.queue-cancelled}",
                "${app.kafka.topic.queue-admitted}",
                "${app.kafka.topic.queue-expired}"
            },
            groupId = "queueflow-audit",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(QueueEventMessage message) {
        // entryId 為 null 代表純隊列大小更新通知，無對應 entry，不寫 audit
        if (message.entryId() == null) {
            return;
        }

        String action = message.eventType().name().replace("QUEUE_", "");
        String idempotencyKey = message.entryId() + ":" + action;

        try {
            QueueAuditLog auditLog = new QueueAuditLog(message.entryId(), action, null);
            auditLog.setIdempotencyKey(idempotencyKey);
            auditLogRepository.save(auditLog);
        } catch (DataIntegrityViolationException e) {
            // unique constraint 衝突代表已處理過（重複消費），安全忽略
            log.warn("Duplicate audit log skipped: key={}", idempotencyKey);
        }
    }
}
