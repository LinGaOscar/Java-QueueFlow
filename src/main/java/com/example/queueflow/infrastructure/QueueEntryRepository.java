package com.example.queueflow.infrastructure;

import com.example.queueflow.domain.EntryStatus;
import com.example.queueflow.domain.QueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    // 查詢指定狀態的入列記錄，避免同一使用者多次入列時的 non-unique-result 問題
    Optional<QueueEntry> findByEventIdAndUserIdAndStatus(Long eventId, String userId, EntryStatus status);

    // 取最近一筆歷史記錄，用於 /me 查詢非 WAITING 的最終狀態
    Optional<QueueEntry> findFirstByEventIdAndUserIdOrderByJoinedAtDesc(Long eventId, String userId);

    List<QueueEntry> findByEventId(Long eventId);

    // 活動已關閉後仍為 WAITING 的記錄才需失效，不應懲罰正在等候的使用者
    @Query("SELECT e FROM QueueEntry e JOIN QueueEvent ev ON e.eventId = ev.id WHERE e.status = 'WAITING' AND ev.status = 'CLOSED'")
    List<QueueEntry> findWaitingEntriesForClosedEvents();
}
