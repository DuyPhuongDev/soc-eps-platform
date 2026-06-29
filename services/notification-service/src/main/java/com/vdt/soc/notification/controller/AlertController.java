package com.vdt.soc.notification.controller;

import com.vdt.soc.notification.dto.AlertResponse;
import com.vdt.soc.notification.entity.Alert;
import com.vdt.soc.notification.repository.AlertRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Alert listing APIs — notification-service is the single source of truth for alerts")
public class AlertController {

    private final AlertRepository alertRepository;

    @GetMapping("/alerts")
    @Operation(summary = "List alerts", description = "Filter by tenantId and/or status")
    public ResponseEntity<List<AlertResponse>> listAlerts(
            @RequestParam(required = false)
            @Parameter(description = "Filter by tenant ID") UUID tenantId,
            @RequestParam(defaultValue = "ACTIVE")
            @Parameter(description = "Alert status: ACTIVE (isRead=false) or ALL") String status) {

        List<Alert> alerts;
        if (tenantId != null) {
            if ("ACTIVE".equalsIgnoreCase(status)) {
                alerts = alertRepository.findByTenantIdAndIsReadFalse(tenantId);
            } else {
                alerts = alertRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
            }
        } else {
            alerts = alertRepository.findAll();
            if ("ACTIVE".equalsIgnoreCase(status)) {
                alerts = alerts.stream().filter(a -> !a.isRead()).toList();
            }
        }

        List<AlertResponse> response = alerts.stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    private AlertResponse toResponse(Alert alert) {
        return AlertResponse.builder()
                .id(alert.getId())
                .tenantId(alert.getTenantId())
                .licenseId(alert.getLicenseId())
                .type(alert.getType())
                .severity(alert.getSeverity())
                .threshold(alert.getThreshold())
                .message(alert.getMessage())
                .isRead(alert.isRead())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
