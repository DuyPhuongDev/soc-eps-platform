package com.vdt.soc.license.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.common.core.dto.SimpleNotificationEvent;
import com.vdt.soc.common.core.enumeration.AlertType;
import com.vdt.soc.license.entity.License;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.events.topic:soc.events.notification}")
    private String notificationTopic;

    @Value("${spring.application.name:license-service}")
    private String sourceService;

    public void publishDeadlineReminder(License license, long dateToExpired) {
        if (license == null || license.getId() == null || license.getEndDate() == null) {
            return;
        }

        String eventId = license.getId() + ":expiration:" + license.getEndDate().truncatedTo(ChronoUnit.DAYS);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("licenseId", license.getId().toString());
        metadata.put("tenantId", license.getTenantId().toString());
        metadata.put("plan", license.getPlan());
        metadata.put("endDate", toIso(license.getEndDate()));
        metadata.put("epsQuota", String.valueOf(license.getEpsQuota()));
        metadata.put("monthlyQuota", String.valueOf(license.getMonthlyQuota()));
        metadata.put("mode", license.getMode().name());
        metadata.put("message", "Your license will be expirated after " + dateToExpired + " days.");
        metadata.put("dateToExpired", Math.max(dateToExpired, 0));

        SimpleNotificationEvent event = SimpleNotificationEvent.builder()
                .eventId(eventId)
                .sourceService(sourceService)
                .eventType(AlertType.LICENSE_EXPIRING.name())
                .targetId(license.getTenantId().toString())
                .metadata(metadata)
                .build();

        publish(event);
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

    private String toIso(Instant value) {
        return value == null ? "" : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
