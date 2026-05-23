package com.example.queueflow.messaging;

import java.time.Instant;

public record QueueEventMessage(
        QueueEventType eventType,
        Long           eventId,
        String         userId,    // null = 純隊列大小更新，無個人通知
        Long           entryId,   // null = 批次更新，AuditConsumer 直接 skip
        Long           queueSize, // null = 個人通知，NotificationConsumer 不推 notifyQueueUpdate
        Instant        occurredAt
) {}
