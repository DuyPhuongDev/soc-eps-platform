package com.vdt.soc.collector.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Batch request wrapping multiple events for efficient ingestion.
 * <p>
 * Agents (Fluentd, Datadog Agent, OTel Collector) flush events in batches —
 * a single HTTP request carrying up to {@value #MAX_BATCH_SIZE} events
 * is far more efficient than individual POSTs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventBatchRequest {

    public static final int MAX_BATCH_SIZE = 1000;

    /**
     * Ordered list of events to ingest.
     * Each EventRequest is validated independently (eventType, timestamp, data).
     */
    @NotEmpty(message = "events must not be empty")
    @Size(min = 1, max = MAX_BATCH_SIZE, message = "batch size must be between 1 and " + MAX_BATCH_SIZE)
    private List<@Valid EventRequest> events;
}
