package com.example.queueflow.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "queue_event")
@Data
@NoArgsConstructor
public class QueueEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.PENDING;

    private Integer capacity;
    private LocalDateTime openTime;
    private LocalDateTime closeTime;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
