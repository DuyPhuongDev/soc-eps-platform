package com.vdt.soc.common.kafka;

/**
 * Centralized Kafka topic name constants shared across services.
 */
public final class KafkaTopics {

    /**
     * Topic prefix for event ingestion. Per-tenant topics: events.{tenantId}
     */
    public static final String EVENT_PREFIX = "events.";
    /**
     * Topic for usage alert events from metric-aggregator (legacy — see ALERT_EVENTS).
     */
    public static final String USAGE_ALERTS = "usage-alerts";

    /**
     * Topic for alert events — fired/resolved by license-service, aggregate-service, etc.
     * Consumed by notification-service for debounce, persistence, and delivery.
     */
    public static final String ALERT_EVENTS = "alert-events";

    private KafkaTopics() {
        // Utility class
    }

    /**
     * Build per-tenant event topic name.
     */
    public static String eventTopic(java.util.UUID tenantId) {
        return EVENT_PREFIX + tenantId;
    }
}