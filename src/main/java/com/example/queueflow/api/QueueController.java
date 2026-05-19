package com.example.queueflow.api;

import com.example.queueflow.application.QueueService;
import com.example.queueflow.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<QueueService.PositionResponse>> join(
            @PathVariable Long eventId,
            @RequestParam String userId) {
        return ResponseEntity.ok(ApiResponse.ok(queueService.joinQueue(eventId, userId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<QueueService.PositionResponse>> getPosition(
            @PathVariable Long eventId,
            @RequestParam String userId) {
        return ResponseEntity.ok(ApiResponse.ok(queueService.getPosition(eventId, userId)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long eventId,
            @RequestParam String userId) {
        queueService.cancelQueue(eventId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
