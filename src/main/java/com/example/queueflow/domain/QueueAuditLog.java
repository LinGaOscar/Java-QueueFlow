package com.example.queueflow.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "queue_audit_log")
@Data
@NoArgsConstructor
public class QueueAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long entryId;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(unique = true, length = 128)
    private String idempotencyKey;

    public QueueAuditLog(Long entryId, String action, String payload) {
        this.entryId = entryId;
        this.action = action;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }
}
