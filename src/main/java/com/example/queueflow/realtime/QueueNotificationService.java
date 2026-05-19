package com.example.queueflow.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class QueueNotificationService {

    private final SimpMessagingTemplate messaging;

    public QueueNotificationService(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void notifyQueueUpdate(Long eventId, Long queueSize) {
        messaging.convertAndSend(
                "/topic/events/" + eventId + "/queue",
                Map.of("queueSize", queueSize));
    }

    public void notifyUserStatus(String userId, String status) {
        messaging.convertAndSend(
                "/topic/users/" + userId + "/queue-status",
                Map.of("status", status));
    }
}
