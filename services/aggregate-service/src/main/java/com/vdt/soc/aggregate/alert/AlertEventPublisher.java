package com.vdt.soc.aggregate.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.common.core.dto.AlertEvent;
import com.vdt.soc.common.core.enumeration.AlertSeverity;
import com.vdt.soc.common.core.enumeration.AlertType;
import com.vdt.soc.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publish a "fire alert" event.
     */
    public void fire(UUID tenantId, AlertType alertType,
                     AlertSeverity severity, String message,
                     Double currentValue, Double threshold) {
        publish(AlertEvent.builder()
                .tenantId(tenantId)
                .alertType(alertType)
                .severity(severity)
                .message(message)
                .currentValue(currentValue)
                .threshold(threshold)
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
                        log.debug("Alert event published: tenant={}, type={}, offset={}",
                                event.getTenantId(), event.getAlertType(),
                                result != null ? result.getRecordMetadata().offset() : -1);
                    }
                });
    }
}
