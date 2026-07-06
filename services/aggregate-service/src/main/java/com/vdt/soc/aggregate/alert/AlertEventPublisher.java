package com.vdt.soc.aggregate.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.common.core.dto.SimpleNotificationEvent;
import com.vdt.soc.common.core.enumeration.AlertSeverity;
import com.vdt.soc.common.core.enumeration.AlertType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.events.topic:soc.events.notification}")
    private String notificationTopic;

    @Value("${spring.application.name:aggregate-service}")
    private String sourceService;

    public void fire(UUID tenantId, AlertType alertType,
                     AlertSeverity severity, String message,
                     Double currentValue, Double threshold) {

        Map<String, Object> metadata = new LinkedHashMap<>();

        metadata.put("tenantId", tenantId != null ? tenantId.toString() : null);
        metadata.put("alertType", alertType != null ? alertType.name() : null);
        metadata.put("severity", severity != null ? severity.name() : null);
        metadata.put("message", message);

        if (currentValue != null && threshold != null && threshold > 0) {
            long percent = Math.round((currentValue / threshold) * 100);
            metadata.put("thresholdPercent", percent);
        } else {
            metadata.put("thresholdPercent", 0);
        }

        metadata.put("currentValue", currentValue);
        metadata.put("threshold", threshold);

        publish(SimpleNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sourceService(sourceService)
                .eventType(alertType != null ? alertType.name() : null)
                .targetId(tenantId != null ? tenantId.toString() : null)
                .metadata(metadata)
                .build());
    }

    private void publish(SimpleNotificationEvent event) {
        try {
            String key = event.getEventId();
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(notificationTopic, key, payload).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed publishing eventType={} topic={} key={} err={}",
                            event.getEventType(), notificationTopic, key, throwable.getMessage(), throwable);
                } else {
                    log.debug("Published eventType={} topic={} key={}", event.getEventType(), notificationTopic, key);
                }
            });
        } catch (Exception ex) {
            log.error("Cannot serialize notification key={}: {}", event.getEventId(), ex.getMessage(), ex);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
