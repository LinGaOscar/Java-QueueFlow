package com.example.queueflow.application;

import com.example.queueflow.common.AppException;
import com.example.queueflow.domain.*;
import com.example.queueflow.infrastructure.QueueEntryRepository;
import com.example.queueflow.infrastructure.QueueEventRepository;
import com.example.queueflow.infrastructure.RedisQueueStore;
import com.example.queueflow.messaging.QueueEventProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class EventService {

    private final QueueEventRepository eventRepository;
    private final QueueEntryRepository entryRepository;
    private final RedisQueueStore redisStore;
    private final QueueEventProducer producer;

    public EventService(QueueEventRepository eventRepository,
                        QueueEntryRepository entryRepository,
                        RedisQueueStore redisStore,
                        QueueEventProducer producer) {
        this.eventRepository = eventRepository;
        this.entryRepository = entryRepository;
        this.redisStore = redisStore;
        this.producer = producer;
    }

    @Transactional(readOnly = true)
    public List<QueueEvent> listEvents() {
        return eventRepository.findAll();
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
                    // 個人通知：queueSize=null，NotificationConsumer 只推個人狀態
                    producer.sendQueueAdmitted(eventId, userId, entry.getId(), null);
                });
                redisStore.removeFromQueue(eventId, userId);
                admitted++;
            }
            // 批次結束後發一次隊列大小更新：userId=null, entryId=null，AuditConsumer 會 skip
            producer.sendQueueAdmitted(eventId, null, null, redisStore.getQueueSize(eventId));
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
