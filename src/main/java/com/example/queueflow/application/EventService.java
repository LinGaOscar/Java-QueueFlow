package com.example.queueflow.application;

import com.example.queueflow.common.AppException;
import com.example.queueflow.domain.*;
import com.example.queueflow.infrastructure.QueueAuditLogRepository;
import com.example.queueflow.infrastructure.QueueEntryRepository;
import com.example.queueflow.infrastructure.QueueEventRepository;
import com.example.queueflow.infrastructure.RedisQueueStore;
import com.example.queueflow.realtime.QueueNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class EventService {

    private final QueueEventRepository eventRepository;
    private final QueueEntryRepository entryRepository;
    private final QueueAuditLogRepository auditLogRepository;
    private final RedisQueueStore redisStore;
    private final QueueNotificationService notificationService;

    public EventService(QueueEventRepository eventRepository,
                        QueueEntryRepository entryRepository,
                        QueueAuditLogRepository auditLogRepository,
                        RedisQueueStore redisStore,
                        QueueNotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.entryRepository = entryRepository;
        this.auditLogRepository = auditLogRepository;
        this.redisStore = redisStore;
        this.notificationService = notificationService;
    }

    public QueueEvent createEvent(String name, Integer capacity,
                                  LocalDateTime openTime, LocalDateTime closeTime) {
        QueueEvent event = new QueueEvent();
        event.setName(name);
        event.setCapacity(capacity);
        event.setOpenTime(openTime);
        event.setCloseTime(closeTime);
        return eventRepository.save(event);
    }

    public QueueEvent openEvent(Long eventId) {
        QueueEvent event = findEvent(eventId);
        event.setStatus(EventStatus.OPEN);
        return eventRepository.save(event);
    }

    public QueueEvent closeEvent(Long eventId) {
        QueueEvent event = findEvent(eventId);
        event.setStatus(EventStatus.CLOSED);
        return eventRepository.save(event);
    }

    public int admitQueue(Long eventId, int count) {
        if (count <= 0) {
            throw AppException.badRequest("放行數量必須大於零");
        }
        // 分散式鎖防止管理端重複觸發同一批次放行
        if (!redisStore.tryAcquireLock(eventId, 10L)) {
            throw AppException.conflict("放行操作正在進行中，請稍後再試");
        }
        try {
            List<String> userIds = redisStore.getTopN(eventId, count);
            int admitted = 0;
            for (String userId : userIds) {
                // 明確查詢 WAITING 狀態，避免使用者多次入列時 non-unique-result
                entryRepository.findByEventIdAndUserIdAndStatus(eventId, userId, EntryStatus.WAITING).ifPresent(entry -> {
                    entry.setStatus(EntryStatus.ADMITTED);
                    entry.setAdmittedAt(LocalDateTime.now());
                    entryRepository.save(entry);
                    auditLogRepository.save(new QueueAuditLog(entry.getId(), "ADMITTED", null));
                });
                redisStore.removeFromQueue(eventId, userId);
                notificationService.notifyUserStatus(userId, "ADMITTED");
                admitted++;
            }
            notificationService.notifyQueueUpdate(eventId, redisStore.getQueueSize(eventId));
            return admitted;
        } finally {
            redisStore.releaseLock(eventId);
        }
    }

    @Transactional(readOnly = true)
    public List<QueueEntry> getQueueList(Long eventId) {
        return entryRepository.findByEventId(eventId);
    }

    private QueueEvent findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> AppException.notFound("活動不存在: " + eventId));
    }
}
