package com.vdt.soc.collector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Inbound event payload from tenant.
 * Minimal validation — collector parses basic structure and forwards the rest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRequest {

    /**
     * Event type (e.g. "log", "metric", "trace"). Used for routing.
     */
    @NotBlank(message = "eventType is required")
    private String eventType;

    /**
     * ISO-8601 timestamp string (e.g. "2026-06-15T10:30:00Z").
     */
    @NotBlank(message = "timestamp is required")
    private String timestamp;

    /**
     * Arbitrary event data. Flexible JSON — collector does not enforce schema.
     */
    @NotNull(message = "data is required")
    private Map<String, Object> data;
}
