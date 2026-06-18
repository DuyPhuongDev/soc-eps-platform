package com.vdt.soc.common.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vdt.soc.common.model.dto.EventEnvelope;

/**
 * JSON serializer for EventEnvelope using the common-model schema.
 */
public class EventEnvelopeSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private EventEnvelopeSerializer() {
        // Utility class
    }

    /**
     * Serialize an EventEnvelope to JSON string.
     */
    public static String toJson(EventEnvelope envelope) {
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize EventEnvelope", e);
        }
    }

    /**
     * Deserialize a JSON string to an EventEnvelope.
     */
    public static EventEnvelope fromJson(String json) {
        try {
            return MAPPER.readValue(json, EventEnvelope.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize EventEnvelope", e);
        }
    }
}