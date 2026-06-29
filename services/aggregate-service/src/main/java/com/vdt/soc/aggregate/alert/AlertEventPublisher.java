package com.vdt.soc.aggregate.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.common.core.dto.AlertEvent;
import com.vdt.soc.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes alert events to Kafka topic {@code alert-events} using
 * Spring Kafka (non-reactive).
 * <p>
 * Replaces the old {@code AlertEngine} which wrote directly to the shared
 * {@code license_db.alerts} table. Now notification-service consumes these
 * events and handles debounce, persistence, and delivery (email, OTT, etc.).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publish a "fire alert" event.
     * The notification-service will debounce and only create a new alert
     * if no active alert of the same type exists for this tenant.
     */
    public void fire(UUID tenantId, UUID licenseId, String alertType,
                     String severity, String message, Integer threshold) {
        publish(AlertEvent.builder()
                .tenantId(tenantId)
                .licenseId(licenseId)
                .alertType(alertType)
                .severity(severity)
                .message(message)
                .threshold(threshold)
                .resolved(false)
                .build());
    }

    /**
     * Publish a "resolve alert" event.
     * The notification-service will mark all active alerts of the given type
     * for this tenant as read.
     */
    public void resolve(UUID tenantId, String alertType) {
        publish(AlertEvent.builder()
                .tenantId(tenantId)
                .alertType(alertType)
                .severity("INFO")
                .message("Auto-resolved")
                .resolved(true)
                .build());
    }

    private void publish(AlertEvent event) {
        String key = event.getTenantId().toString();
        String value;
        try {
            value = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize AlertEvent for tenant={}, type={}: {}",
                    event.getTenantId(), event.getAlertType(), e.getMessage());
            return;
        }

        kafkaTemplate.send(KafkaTopics.ALERT_EVENTS, key, value)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish alert event: tenant={}, type={}: {}",
                                event.getTenantId(), event.getAlertType(), ex.getMessage());
                    } else {
                        log.debug("Alert event published: tenant={}, type={}, resolved={}, offset={}",
                                event.getTenantId(), event.getAlertType(), event.isResolved(),
                                result != null ? result.getRecordMetadata().offset() : -1);
                    }
                });
    }
}
