package com.vdt.soc.common.kafka;

/**
 * Centralized Kafka topic name constants shared across services.
 */
public final class KafkaTopics {

    private KafkaTopics() {
        // Utility class
    }

    /**
     * Topic prefix for event ingestion. Per-tenant topics: events.{tenantId}
     */
    public static final String EVENT_PREFIX = "events.";

    /**
     * Topic for usage alert events from metric-aggregator.
     */
    public static final String USAGE_ALERTS = "usage-alerts";

    /**
     * Build per-tenant event topic name.
     */
    public static String eventTopic(java.util.UUID tenantId) {
        return EVENT_PREFIX + tenantId;
    }
}