package com.vdt.soc.common.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standard Kafka event envelope — the schema contract between
 * collector-service (producer) and metric-aggregator (consumer).
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {

    private UUID tenantId;
    private String eventType;
    private Instant timestamp;
    private Map<String, Object> data;
    private Instant ingestTimestamp;
    private String traceId;
}
