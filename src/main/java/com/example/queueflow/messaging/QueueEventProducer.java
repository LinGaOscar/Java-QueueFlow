package com.example.queueflow.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Component
public class QueueEventProducer {

    private static final Logger log = LoggerFactory.getLogger(QueueEventProducer.class);

    private final KafkaTemplate<String, QueueEventMessage> kafkaTemplate;

    @Value("${app.kafka.topic.queue-created}")   private String topicCreated;
    @Value("${app.kafka.topic.queue-admitted}")  private String topicAdmitted;
    @Value("${app.kafka.topic.queue-cancelled}") private String topicCancelled;
    @Value("${app.kafka.topic.queue-expired}")   private String topicExpired;

    public QueueEventProducer(KafkaTemplate<String, QueueEventMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendQueueCreated(Long eventId, String userId, Long entryId, Long queueSize) {
        send(topicCreated, eventId,
                new QueueEventMessage(QueueEventType.QUEUE_CREATED, eventId, userId, entryId, queueSize, Instant.now()));
    }

    public void sendQueueCancelled(Long eventId, String userId, Long entryId, Long queueSize) {
        send(topicCancelled, eventId,
                new QueueEventMessage(QueueEventType.QUEUE_CANCELLED, eventId, userId, entryId, queueSize, Instant.now()));
    }

    public void sendQueueAdmitted(Long eventId, String userId, Long entryId, Long queueSize) {
        send(topicAdmitted, eventId,
                new QueueEventMessage(QueueEventType.QUEUE_ADMITTED, eventId, userId, entryId, queueSize, Instant.now()));
    }

    public void sendQueueExpired(Long eventId, String userId, Long entryId, Long queueSize) {
        send(topicExpired, eventId,
                new QueueEventMessage(QueueEventType.QUEUE_EXPIRED, eventId, userId, entryId, queueSize, Instant.now()));
    }

    private void send(String topic, Long eventId, QueueEventMessage message) {
        // 若在 DB transaction 內，延至 commit 後才發出，避免 Consumer 收到事件時 FK row 尚未可見
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(topic, eventId, message);
                }
            });
        } else {
            doSend(topic, eventId, message);
        }
    }

    private void doSend(String topic, Long eventId, QueueEventMessage message) {
        // 以 eventId 為 partition key，確保同一活動的事件進入同一 partition（有序消費）
        kafkaTemplate.send(topic, String.valueOf(eventId), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka send failed: topic={}, eventId={}, type={}",
                                topic, eventId, message.eventType(), ex);
                    }
                });
    }
}
