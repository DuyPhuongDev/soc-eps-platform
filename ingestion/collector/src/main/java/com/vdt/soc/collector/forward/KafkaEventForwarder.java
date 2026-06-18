package com.vdt.soc.collector.forward;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.collector.dto.EventRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.UUID;

/**
 * Forwards accepted events to Kafka for downstream aggregate-service consumption.
 * <p>
 * Topic pattern: events.{tenantId}
 * Partition key: tenantId.toString() (ensures per-tenant ordering)
 * <p>
 * Errors are propagated to caller (EnforcementEngine) — if Kafka is down,
 * the engine returns HTTP 503.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventForwarder {

    private static final String TOPIC_PREFIX = "events.";

    private final KafkaSender<String, String> kafkaSender;
    private final ObjectMapper objectMapper;

    /**
     * Forward an accepted event to Kafka.
     *
     * @param event    the validated event
     * @param tenantId tenant identifier (used for topic and partition key)
     * @return Mono<Void> that completes when Kafka acknowledges,
     * or errors if Kafka is unreachable
     */
    public Mono<Void> send(EventRequest event, UUID tenantId) {
        String topic = TOPIC_PREFIX + tenantId;
        String key = tenantId.toString();

        String value;
        try {
            value = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalArgumentException("Failed to serialize event: " + e.getMessage()));
        }

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        SenderRecord<String, String, String> senderRecord = SenderRecord.create(record, record.key());

        return kafkaSender.send(Mono.just(senderRecord))
                .singleOrEmpty()
                .flatMap(result -> {
                    if (result.exception() != null) {
                        log.warn("Kafka send failed for topic={}: {}", topic, result.exception().getMessage());
                        return Mono.error(result.exception());
                    }
                    log.debug("Event forwarded: topic={}, offset={}", topic,
                            result.recordMetadata() != null ? result.recordMetadata().offset() : -1);
                    return Mono.empty();
                });
    }
}
