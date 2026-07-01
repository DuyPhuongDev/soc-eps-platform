package com.vdt.soc.aggregate.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Alerts are now handled by notification-service.
 * This controller redirects clients to the notification-service API.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Alert redirect — alerts are managed by notification-service")
public class AlertController {

    @GetMapping("/alerts")
    @Operation(summary = "Redirect to notification-service",
            description = "Alerts are now managed by notification-service on port 8085. "
                        + "Call GET /api/v1/alerts on notification-service instead.")
    public ResponseEntity<Map<String, Object>> redirectToNotificationService() {
        return ResponseEntity.ok(Map.of(
                "message", "Alerts are managed by notification-service",
                "redirect", "GET http://localhost:8085/api/v1/alerts",
                "note", "Alerts are published via Kafka topic 'alert-events' and persisted by notification-service"
        ));
    }
}
