package com.example.queueflow.batch;

import com.example.queueflow.domain.EntryStatus;
import com.example.queueflow.domain.QueueAuditLog;
import com.example.queueflow.domain.QueueEntry;
import com.example.queueflow.infrastructure.QueueAuditLogRepository;
import com.example.queueflow.infrastructure.QueueEntryRepository;
import com.example.queueflow.infrastructure.RedisQueueStore;
import com.example.queueflow.realtime.QueueNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class QueueExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(QueueExpiryScheduler.class);

    private final QueueEntryRepository entryRepository;
    private final QueueAuditLogRepository auditLogRepository;
    private final RedisQueueStore redisStore;
    private final QueueNotificationService notificationService;

    public QueueExpiryScheduler(QueueEntryRepository entryRepository,
                                 QueueAuditLogRepository auditLogRepository,
                                 RedisQueueStore redisStore,
                                 QueueNotificationService notificationService) {
        this.entryRepository = entryRepository;
        this.auditLogRepository = auditLogRepository;
        this.redisStore = redisStore;
        this.notificationService = notificationService;
    }

    // 只失效「活動已關閉」後仍為 WAITING 的記錄，不影響正在等候中的使用者
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStaleEntries() {
        List<QueueEntry> stale = entryRepository.findWaitingEntriesForClosedEvents();
        if (stale.isEmpty()) return;

        log.info("活動已關閉，清除剩餘候補記錄: {} 筆", stale.size());
        for (QueueEntry entry : stale) {
            entry.setStatus(EntryStatus.EXPIRED);
            entry.setExpiredAt(LocalDateTime.now());
            entryRepository.save(entry);
            auditLogRepository.save(new QueueAuditLog(entry.getId(), "EXPIRED", null));
            redisStore.removeFromQueue(entry.getEventId(), entry.getUserId());
            notificationService.notifyUserStatus(entry.getUserId(), "EXPIRED");
        }

        stale.stream()
                .map(QueueEntry::getEventId)
                .distinct()
                .forEach(eventId -> notificationService.notifyQueueUpdate(
                        eventId, redisStore.getQueueSize(eventId)));
    }
}
