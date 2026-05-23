package com.example.queueflow.messaging;

import com.example.queueflow.realtime.QueueNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final QueueNotificationService notificationService;

    public NotificationConsumer(QueueNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = {
                "${app.kafka.topic.queue-created}",
                "${app.kafka.topic.queue-cancelled}",
                "${app.kafka.topic.queue-admitted}",
                "${app.kafka.topic.queue-expired}"
            },
            groupId = "queueflow-notification",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(QueueEventMessage message) {
        try {
            switch (message.eventType()) {
                case QUEUE_CREATED, QUEUE_CANCELLED -> {
                    if (message.queueSize() != null) {
                        notificationService.notifyQueueUpdate(message.eventId(), message.queueSize());
                    }
                }
                case QUEUE_ADMITTED -> {
                    if (message.userId() != null) {
                        notificationService.notifyUserStatus(message.userId(), "ADMITTED");
                    }
                    if (message.queueSize() != null) {
                        notificationService.notifyQueueUpdate(message.eventId(), message.queueSize());
                    }
                }
                case QUEUE_EXPIRED -> {
                    if (message.userId() != null) {
                        notificationService.notifyUserStatus(message.userId(), "EXPIRED");
                    }
                    if (message.queueSize() != null) {
                        notificationService.notifyQueueUpdate(message.eventId(), message.queueSize());
                    }
                }
            }
        } catch (Exception e) {
            // WebSocket 失敗不 re-throw，避免觸發 Kafka retry 造成重複推播
            log.error("NotificationConsumer error: type={}, entryId={}", message.eventType(), message.entryId(), e);
        }
    }
}
