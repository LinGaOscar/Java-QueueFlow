package com.example.queueflow.api;

import com.example.queueflow.application.EventService;
import com.example.queueflow.common.ApiResponse;
import com.example.queueflow.domain.QueueEntry;
import com.example.queueflow.domain.QueueEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final EventService eventService;

    public AdminController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<QueueEvent>>> listEvents() {
        return ResponseEntity.ok(ApiResponse.ok(eventService.listEvents()));
    }

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<QueueEvent>> createEvent(@RequestBody CreateEventRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                eventService.createEvent(req.name(), req.capacity(), req.openTime(), req.closeTime())));
    }

    @PostMapping("/events/{eventId}/open")
    public ResponseEntity<ApiResponse<QueueEvent>> openEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.openEvent(eventId)));
    }

    @PostMapping("/events/{eventId}/close")
    public ResponseEntity<ApiResponse<QueueEvent>> closeEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.closeEvent(eventId)));
    }

    @PostMapping("/events/{eventId}/admit")
    public ResponseEntity<ApiResponse<Integer>> admitQueue(
            @PathVariable Long eventId,
            @RequestBody AdmitRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.admitQueue(eventId, req.count())));
    }

    @GetMapping("/events/{eventId}/queue")
    public ResponseEntity<ApiResponse<List<QueueEntry>>> getQueueList(@PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.getQueueList(eventId)));
    }

    record CreateEventRequest(String name, Integer capacity,
                              LocalDateTime openTime, LocalDateTime closeTime) {}

    record AdmitRequest(int count) {}
}
