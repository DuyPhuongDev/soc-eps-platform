package com.vdt.soc.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.common.core.dto.SimpleNotificationEvent;
import com.vdt.soc.common.core.enumeration.AlertSeverity;
import com.vdt.soc.common.core.enumeration.AlertType;
import com.vdt.soc.common.core.exception.BadRequestException;
import com.vdt.soc.notification.entity.Alert;
import com.vdt.soc.notification.mail.AlertMailer;
import com.vdt.soc.notification.repository.AlertRepository;
import com.vdt.soc.notification.util.JsonCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationInboundService {

    private final AlertRepository alertRepository;
    private final AlertMailer alertMailer;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handleInboundEvent(String payload) {
        SimpleNotificationEvent event;
        try {
            event = objectMapper.readValue(payload, SimpleNotificationEvent.class);
        } catch (Exception ex) {
            throw new BadRequestException("Invalid notification event payload: " + ex.getMessage());
        }

        if (!StringUtils.hasText(event.getEventId())) {
            throw new BadRequestException("messageId is required");
        }

        String sourceServiceRaw = StringUtils.hasText(event.getSourceService()) ? event.getSourceService().trim() : "";

        if ("notification-service".equalsIgnoreCase(sourceServiceRaw)) {
            log.debug("Skip internal notification event sourceService={}", sourceServiceRaw);
            return;
        }

        List<Alert> active = alertRepository.findByTenantIdAndTypeAndIsReadFalse(
                UUID.fromString(event.getTargetId()), AlertType.valueOf(event.getEventType()));
        if (!active.isEmpty()) {
            log.debug("Alert suppressed (already active): tenant={}, type={}",
                    event.getTargetId(), event.getEventType());
            return;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        if (event.getMetadata() != null) {
            meta.putAll(event.getMetadata());
        }
        meta.put("occurredAt", Instant.now().toString());

        String severity = event.getMetadata().getOrDefault("severity", "INFO").toString();
        String message = event.getMetadata().getOrDefault("message", "New alert").toString();
        Double currentValue = Double.valueOf(event.getMetadata().getOrDefault("currentValue", 0.0).toString());
        Double threshold = Double.valueOf(event.getMetadata().getOrDefault("threshold", 0.0).toString());


        Alert alert = Alert.builder()
                .tenantId(UUID.fromString(event.getTargetId()))
                .type(AlertType.valueOf(event.getEventType()))
                .severity(AlertSeverity.valueOf(severity))
                .message(message)
                .currentValue(currentValue)
                .threshold(threshold)
                .isRead(false)
                .build();

        alertRepository.save(alert);
        alertMailer.send(alert, event.getMetadata());
        log.info("Alert persisted: tenant={}, type={}, severity={}",
                event.getTargetId(), event.getEventType(), severity);
    }
}

