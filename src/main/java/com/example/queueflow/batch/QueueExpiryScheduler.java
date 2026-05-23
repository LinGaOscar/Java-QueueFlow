package com.example.queueflow.batch;

import com.example.queueflow.domain.EntryStatus;
import com.example.queueflow.domain.QueueEntry;
import com.example.queueflow.infrastructure.QueueEntryRepository;
import com.example.queueflow.infrastructure.RedisQueueStore;
import com.example.queueflow.messaging.QueueEventProducer;
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
    private final RedisQueueStore redisStore;
    private final QueueEventProducer producer;

    public QueueExpiryScheduler(QueueEntryRepository entryRepository,
                                 RedisQueueStore redisStore,
                                 QueueEventProducer producer) {
        this.entryRepository = entryRepository;
        this.redisStore = redisStore;
        this.producer = producer;
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
            redisStore.removeFromQueue(entry.getEventId(), entry.getUserId());
            // 個人通知：queueSize=null，NotificationConsumer 只推個人狀態
            producer.sendQueueExpired(entry.getEventId(), entry.getUserId(), entry.getId(), null);
        }

        // 每個受影響活動各發一次隊列大小更新：userId=null, entryId=null，AuditConsumer 會 skip
        stale.stream()
                .map(QueueEntry::getEventId)
                .distinct()
                .forEach(eventId ->
                        producer.sendQueueExpired(eventId, null, null, redisStore.getQueueSize(eventId)));
    }
}
