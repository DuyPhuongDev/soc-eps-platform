package com.vdt.soc.common.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

/**
 * Kafka event published when a service wants to fire or resolve an alert.
 * Consumed by notification-service which handles debounce, persistence,
 * and future delivery channels (email, OTT, webhook).
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@code resolved = false} — fire a new alert (if not already active)</li>
 *   <li>{@code resolved = true} — mark all active alerts of this type for the tenant as read</li>
 * </ul>
 */
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {

    /** Tenant that the alert belongs to. */
    private UUID tenantId;

    /** Optional license ID — set for LICENSE_EXPIRING, may be null for EPS/quota alerts. */
    private UUID licenseId;

    /**
     * Alert type string matching {@link com.vdt.soc.common.core.enumeration.AlertType}.
     * E.g. "EPS_70_PCT", "EPS_100_PCT", "MONTHLY_QUOTA_100_PCT", "LICENSE_EXPIRING".
     */
    private String alertType;

    /** "WARNING", "CRITICAL", "INFO". */
    private String severity;

    /** Human-readable alert message. */
    private String message;

    /** Optional threshold value that triggered the alert. */
    private Integer threshold;

    /** {@code true} if this event resolves (clears) the alert rather than firing it. */
    @Builder.Default
    private boolean resolved = false;
}
