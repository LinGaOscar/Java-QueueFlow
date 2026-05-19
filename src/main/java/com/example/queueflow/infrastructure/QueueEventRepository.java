package com.example.queueflow.infrastructure;

import com.example.queueflow.domain.QueueEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueEventRepository extends JpaRepository<QueueEvent, Long> {
}
