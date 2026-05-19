package com.example.queueflow.application;

import com.example.queueflow.common.AppException;
import com.example.queueflow.domain.EntryStatus;
import com.example.queueflow.domain.EventStatus;
import com.example.queueflow.domain.QueueAuditLog;
import com.example.queueflow.domain.QueueEntry;
import com.example.queueflow.domain.QueueEvent;
import com.example.queueflow.infrastructure.QueueAuditLogRepository;
import com.example.queueflow.infrastructure.QueueEntryRepository;
import com.example.queueflow.infrastructure.QueueEventRepository;
import com.example.queueflow.infrastructure.RedisQueueStore;
import com.example.queueflow.realtime.QueueNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class QueueService {

    private final QueueEventRepository eventRepository;
    private final QueueEntryRepository entryRepository;
    private final QueueAuditLogRepository auditLogRepository;
    private final RedisQueueStore redisStore;
    private final QueueNotificationService notificationService;

    public QueueService(QueueEventRepository eventRepository,
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

    // 同步寫入 DB：確保 cancel/admit 在 joinQueue 回應前已能查到此記錄，
    // 避免非同步寫入造成的狀態不一致（Phase 2 可改為 Kafka 事件驅動）
    @Transactional
    public PositionResponse joinQueue(Long eventId, String userId) {
        if (redisStore.isRateLimited(userId)) {
            throw AppException.tooManyRequests("請求過於頻繁，請稍後再試");
        }

        QueueEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> AppException.notFound("活動不存在"));
        if (event.getStatus() != EventStatus.OPEN) {
            throw AppException.badRequest("活動目前不開放候補");
        }

        long score = redisStore.nextSequence(eventId);
        boolean joined = redisStore.tryJoin(eventId, userId, score, 86400L);
        if (!joined) {
            throw AppException.conflict("您已在候補名單中");
        }

        QueueEntry entry = new QueueEntry();
        entry.setEventId(eventId);
        entry.setUserId(userId);
        QueueEntry saved = entryRepository.save(entry);
        auditLogRepository.save(new QueueAuditLog(saved.getId(), "JOIN", null));

        Long position = redisStore.getPosition(eventId, userId);
        notificationService.notifyQueueUpdate(eventId, redisStore.getQueueSize(eventId));

        return new PositionResponse(position, position - 1, "WAITING");
    }

    @Transactional(readOnly = true)
    public PositionResponse getPosition(Long eventId, String userId) {
        Long position = redisStore.getPosition(eventId, userId);
        if (position != null) {
            return new PositionResponse(position, position - 1, "WAITING");
        }
        // Redis 無記錄時查最近一筆 DB 歷史狀態（使用者可能已結束或多次入列）
        QueueEntry entry = entryRepository.findFirstByEventIdAndUserIdOrderByJoinedAtDesc(eventId, userId)
                .orElseThrow(() -> AppException.notFound("查無排隊記錄"));
        return new PositionResponse(0L, 0L, entry.getStatus().name());
    }

    @Transactional
    public void cancelQueue(Long eventId, String userId) {
        if (!redisStore.isInQueue(eventId, userId)) {
            throw AppException.notFound("您不在候補名單中");
        }
        redisStore.removeFromQueue(eventId, userId);

        // 明確查詢 WAITING 狀態，避免使用者多次入列時的 non-unique-result 問題
        entryRepository.findByEventIdAndUserIdAndStatus(eventId, userId, EntryStatus.WAITING)
                .ifPresent(entry -> {
                    entry.setStatus(EntryStatus.CANCELLED);
                    entry.setCancelledAt(LocalDateTime.now());
                    entryRepository.save(entry);
                    auditLogRepository.save(new QueueAuditLog(entry.getId(), "CANCELLED", null));
                });

        notificationService.notifyQueueUpdate(eventId, redisStore.getQueueSize(eventId));
    }

    public record PositionResponse(long position, long ahead, String status) {}
}
