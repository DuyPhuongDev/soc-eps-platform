package com.vdt.soc.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.common.core.dto.AlertEvent;
import com.vdt.soc.common.kafka.KafkaTopics;
import com.vdt.soc.notification.engine.AlertEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes alert events from Kafka topic {@code alert-events}
 * and delegates to {@link AlertEngine} for persistence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventConsumer {

    private final AlertEngine alertEngine;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.ALERT_EVENTS, groupId = "notification-service")
    public void onAlertEvent(String message) {
        try {
            AlertEvent event = objectMapper.readValue(message, AlertEvent.class);
            log.debug("Received alert event: tenant={}, type={}",
                    event.getTenantId(), event.getAlertType());
            alertEngine.process(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize AlertEvent: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing alert event: {}", e.getMessage(), e);
        }
    }
}
