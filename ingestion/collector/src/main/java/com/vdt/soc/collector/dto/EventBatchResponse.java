package com.vdt.soc.collector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response for batch event ingestion.
 * <p>
 * Reports how many events were accepted vs rejected due to rate limiting.
 * The agent can use this to decide whether to retry rejected events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventBatchResponse {

    /**
     * Tenant resolved from API key.
     */
    private UUID tenantId;

    /**
     * Correlation id for tracing this batch through the pipeline.
     */
    private String traceId;

    /**
     * Number of events accepted and forwarded to Kafka.
     */
    private int accepted;

    /**
     * Number of events rejected due to EPS quota.
     */
    private int rejected;

    /**
     * Overall batch status:
     * <ul>
     *   <li>{@code "accepted"} — all events in the batch were accepted</li>
     *   <li>{@code "partial"} — some events accepted, some throttled</li>
     * </ul>
     * <p>
     * When the entire batch is throttled (accepted == 0), the response
     * is HTTP 429 rather than 202 with status="throttled".
     */
    private String status;
}
