package com.vdt.soc.collector.forward;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.collector.dto.EventRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.List;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventForwarder {

    private static final String TOPIC = "events";

    private final KafkaSender<String, String> kafkaSender;
    private final ObjectMapper objectMapper;

    public Mono<Void> send(EventRequest event, UUID tenantId) {
        String topic = TOPIC;
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

    public Mono<Void> sendBatch(List<EventRequest> events, UUID tenantId) {
        String topic = TOPIC;
        String key = tenantId.toString();

        Flux<SenderRecord<String, String, String>> records = Flux.fromIterable(events)
                .map(event -> {
                    try {
                        return objectMapper.writeValueAsString(event);
                    } catch (JsonProcessingException e) {
                        throw new IllegalArgumentException(
                                "Failed to serialize event: " + e.getMessage(), e);
                    }
                })
                .map(value -> {
                    ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
                    return SenderRecord.create(record, record.key());
                });

        return kafkaSender.send(records)
                .then()
                .doOnError(e -> log.warn("Kafka batch send failed for topic={}: {}", topic, e.getMessage()))
                .doOnSuccess(v -> log.debug("Batch forwarded: topic={}, count={}", topic, events.size()));
    }
}
